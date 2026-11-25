package vn.uit.healthos.feature.friends

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import dagger.multibindings.StringKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import vn.uit.healthos.core.data.outbox.OutboxHandler
import vn.uit.healthos.core.network.ApiError
import vn.uit.healthos.core.network.ApiResult
import vn.uit.healthos.core.network.RealtimeMessage
import javax.inject.Inject
import javax.inject.Singleton

const val OUTBOX_TYPE_DM_SEND = "dm.send"

@Serializable
data class DmSendPayload(
    @SerialName("conversation_id") val conversationId: String,
    val content: String,
    @SerialName("client_message_id") val clientMessageId: String,
)

/** The REST call the handler makes; split out so the handler is testable without Retrofit. */
fun interface DmSender {
    suspend fun send(payload: DmSendPayload): ApiResult<RealtimeMessage>
}

/** Writes the outcome of a queued send to the local cache. */
interface DmSendResultStore {
    suspend fun delivered(message: RealtimeMessage)
    suspend fun failed(clientMessageId: String)
}

/**
 * Delivers direct messages that could not go over the socket (offline, socket down, or no ack in time) through
 * `POST /v1/conversations/{id}/messages`. The server deduplicates on `client_message_id`, so a message that did reach
 * it over the socket is not stored twice.
 */
@Singleton
class DmOutboxHandler @Inject constructor(
    private val sender: DmSender,
    private val results: DmSendResultStore,
    private val json: Json,
) : OutboxHandler {

    override suspend fun send(payloadJson: String): ApiResult<Unit> {
        val payload = decode(payloadJson).getOrElse { e ->
            return ApiResult.Failure(ApiError(ApiError.PARSE, e.message ?: "Invalid DM payload"))
        }
        return when (val result = sender.send(payload)) {
            is ApiResult.Success -> {
                results.delivered(result.data)
                ApiResult.Success(Unit)
            }
            is ApiResult.Failure -> {
                val error = membershipVerdict(result.error)
                if (!error.isRetryable && error.httpStatus != HTTP_UNAUTHORIZED && error.httpStatus != HTTP_FORBIDDEN) {
                    results.failed(payload.clientMessageId)
                }
                ApiResult.Failure(error)
            }
        }
    }

    /** The outbox gave up after repeated transient failures: show the message as failed so the user can retry. */
    override suspend fun onDropped(payloadJson: String) {
        decode(payloadJson).getOrNull()?.let { results.failed(it.clientMessageId) }
    }

    private fun decode(payloadJson: String): Result<DmSendPayload> = try {
        Result.success(json.decodeFromString(DmSendPayload.serializer(), payloadJson))
    } catch (e: SerializationException) {
        Result.failure(e)
    } catch (e: IllegalArgumentException) {
        Result.failure(e)
    }
}

/**
 * `CHAT_FORBIDDEN` (403) means the sender is no longer a member of the conversation (unfriended): a final verdict,
 * not a session problem. It is reported without the HTTP status so the outbox drops the item instead of holding it
 * for a new session; any other error passes through unchanged.
 */
internal fun membershipVerdict(error: ApiError): ApiError =
    if (error.httpStatus == HTTP_FORBIDDEN && error.code == CHAT_FORBIDDEN) error.copy(httpStatus = null) else error

private const val CHAT_FORBIDDEN = "CHAT_FORBIDDEN"
private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403

@Module
@InstallIn(SingletonComponent::class)
abstract class DmOutboxModule {
    @Binds
    @IntoMap
    @StringKey(OUTBOX_TYPE_DM_SEND)
    abstract fun bindDmSendHandler(handler: DmOutboxHandler): OutboxHandler

    @Binds
    abstract fun bindDmSender(impl: RestDmSender): DmSender

    @Binds
    abstract fun bindDmSendResultStore(impl: RoomDmSendResultStore): DmSendResultStore
}
