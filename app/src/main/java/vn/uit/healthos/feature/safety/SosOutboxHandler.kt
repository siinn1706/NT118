package vn.uit.healthos.feature.safety

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import dagger.multibindings.StringKey
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import vn.uit.healthos.core.data.outbox.OutboxHandler
import vn.uit.healthos.core.network.ApiError
import vn.uit.healthos.core.network.ApiResult
import vn.uit.healthos.core.network.apiCall
import javax.inject.Inject
import javax.inject.Singleton

const val OUTBOX_TYPE_SOS_EMAIL = "sos.email"

/**
 * Delivers queued SOS alert emails (`POST /v1/emergency/sos`). The endpoint may not be deployed yet, so a 404 is
 * reported as retryable: the alert stays queued (bounded by the outbox attempt limit) instead of being dropped.
 */
@Singleton
class SosOutboxHandler @Inject constructor(
    private val sosApi: SosApi,
    private val json: Json,
) : OutboxHandler {

    override suspend fun send(payloadJson: String): ApiResult<Unit> {
        val body = try {
            json.decodeFromString(SosEmailRequestBody.serializer(), payloadJson)
        } catch (e: SerializationException) {
            return ApiResult.Failure(ApiError(ApiError.PARSE, e.message ?: "Invalid SOS payload"))
        } catch (e: IllegalArgumentException) {
            return ApiResult.Failure(ApiError(ApiError.PARSE, e.message ?: "Invalid SOS payload"))
        }
        return when (val result = apiCall(json) { sosApi.sendSosEmail(body) }) {
            is ApiResult.Success -> ApiResult.Success(Unit)
            is ApiResult.Failure -> ApiResult.Failure(retryWhileEndpointMissing(result.error))
        }
    }
}

/** Maps HTTP 404 to a retryable network-class error; every other error passes through unchanged. */
internal fun retryWhileEndpointMissing(error: ApiError): ApiError =
    if (error.httpStatus == HTTP_NOT_FOUND) {
        error.copy(code = ApiError.NETWORK, message = "SOS endpoint unavailable (HTTP 404): ${error.message}")
    } else {
        error
    }

private const val HTTP_NOT_FOUND = 404

@Module
@InstallIn(SingletonComponent::class)
abstract class SosOutboxModule {
    @Binds
    @IntoMap
    @StringKey(OUTBOX_TYPE_SOS_EMAIL)
    abstract fun bindSosEmailHandler(handler: SosOutboxHandler): OutboxHandler
}
