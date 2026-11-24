package vn.uit.healthos.feature.friends

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import vn.uit.healthos.core.data.outbox.OutboxRepository
import vn.uit.healthos.core.data.session.TokenStore
import vn.uit.healthos.core.network.ApiResult
import vn.uit.healthos.core.network.RealtimeEvent
import vn.uit.healthos.core.network.RealtimeMessage
import vn.uit.healthos.core.network.RealtimeSocket
import vn.uit.healthos.core.network.RealtimeState
import vn.uit.healthos.core.network.apiCall
import java.time.Instant
import java.time.OffsetDateTime
import javax.inject.Inject
import javax.inject.Singleton

/** One page of history: [nextCursor] is the `before` value for the next older page. */
data class DmPage(val hasMore: Boolean, val nextCursor: String?)

interface DirectChatRepository {
    val events: Flow<RealtimeEvent>
    val connection: StateFlow<RealtimeState>

    fun currentUserId(): String?
    fun observeMessages(conversationId: String): Flow<List<DmMessageEntity>>

    /**
     * Fetches the newest page into the cache (also the catch-up after a reconnect). With [trimCache] the cache is cut
     * back to the most recent messages first; only done when the screen opens, so older pages being read stay put.
     */
    suspend fun loadLatest(conversationId: String, trimCache: Boolean): ApiResult<DmPage>
    suspend fun loadOlder(conversationId: String, before: String): ApiResult<DmPage>

    /** Writes the optimistic copy shown until the server confirms it. */
    suspend fun insertPending(conversationId: String, content: String, clientMessageId: String)

    /** Sends over the socket; false when it is not connected. */
    fun sendOverSocket(conversationId: String, content: String, clientMessageId: String): Boolean

    /** Queues the message for REST delivery through the outbox (offline queue and socket fallback). */
    suspend fun enqueue(conversationId: String, content: String, clientMessageId: String)

    /**
     * Waits for the socket's `msg:ack` and queues the message for REST when none arrived in time. Runs outside the
     * screen's lifetime, so leaving the chat right after sending cannot strand a message.
     */
    fun queueIfUnacknowledged(conversationId: String, content: String, clientMessageId: String)

    /** Replaces the optimistic copy with the server's id and time. */
    suspend fun applyAck(clientMessageId: String, serverMessageId: String, createdAt: String?)
    suspend fun store(message: RealtimeMessage)
    suspend fun markPending(clientMessageId: String)
    suspend fun markRead(conversationId: String, lastMessageId: String): ApiResult<Unit>
    fun sendTyping(conversationId: String, isTyping: Boolean)
    fun joinRoom(conversationId: String)
}

/** Maps a server message into the cache. */
fun RealtimeMessage.toEntity(): DmMessageEntity = DmMessageEntity(
    id = id,
    conversationId = conversationId,
    senderId = senderId,
    content = content,
    clientMessageId = clientMessageId,
    createdAtMs = parseServerTime(createdAt) ?: System.currentTimeMillis(),
    status = DmStatus.SENT,
)

/** Server timestamps are ISO-8601 with an offset (`+00:00`) or `Z`. */
fun parseServerTime(value: String?): Long? {
    if (value.isNullOrBlank()) return null
    return runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }.getOrNull()
        ?: runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
}

@Singleton
class RoomDirectChatRepository @Inject constructor(
    private val api: FriendsApi,
    private val dao: DmMessageDao,
    private val realtime: RealtimeSocket,
    private val outboxRepository: OutboxRepository,
    private val tokenStore: TokenStore,
    private val json: Json,
) : DirectChatRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        realtime.start()
    }

    override val events: Flow<RealtimeEvent> = realtime.events
    override val connection: StateFlow<RealtimeState> = realtime.state

    override fun currentUserId(): String? = tokenStore.current()?.userId

    override fun observeMessages(conversationId: String) = dao.observeConversation(conversationId)

    override suspend fun loadLatest(conversationId: String, trimCache: Boolean): ApiResult<DmPage> {
        if (trimCache) dao.trim(conversationId, CACHE_KEEP)
        return page(conversationId, before = null)
    }

    override suspend fun loadOlder(conversationId: String, before: String) = page(conversationId, before)

    private suspend fun page(conversationId: String, before: String?): ApiResult<DmPage> =
        when (val result = apiCall(json) { api.messages(conversationId, before, PAGE_SIZE) }) {
            is ApiResult.Success -> {
                dao.putServerMessages(result.data.data.map { it.toEntity() })
                ApiResult.Success(DmPage(result.data.hasMore, result.data.nextCursor))
            }
            is ApiResult.Failure -> result
        }

    override suspend fun insertPending(conversationId: String, content: String, clientMessageId: String) {
        dao.upsert(
            listOf(
                DmMessageEntity(
                    id = DmMessageEntity.localId(clientMessageId),
                    conversationId = conversationId,
                    senderId = currentUserId(),
                    content = content,
                    clientMessageId = clientMessageId,
                    createdAtMs = System.currentTimeMillis(),
                    status = DmStatus.PENDING,
                ),
            ),
        )
    }

    override fun sendOverSocket(conversationId: String, content: String, clientMessageId: String): Boolean =
        realtime.send(
            "msg:send",
            JsonObject(
                mapOf(
                    "conversation_id" to JsonPrimitive(conversationId),
                    "content" to JsonPrimitive(content),
                    "content_type" to JsonPrimitive("text"),
                    "client_message_id" to JsonPrimitive(clientMessageId),
                ),
            ),
        )

    override suspend fun enqueue(conversationId: String, content: String, clientMessageId: String) {
        outboxRepository.enqueue(
            type = OUTBOX_TYPE_DM_SEND,
            payload = DmSendPayload(conversationId, content, clientMessageId),
            serializer = DmSendPayload.serializer(),
            dedupeKey = "$OUTBOX_TYPE_DM_SEND:$clientMessageId",
        )
    }

    override fun queueIfUnacknowledged(conversationId: String, content: String, clientMessageId: String) {
        scope.launch {
            delay(ACK_TIMEOUT_MS)
            if (dao.byClientId(clientMessageId)?.status != DmStatus.SENT) enqueue(conversationId, content, clientMessageId)
        }
    }

    override suspend fun applyAck(clientMessageId: String, serverMessageId: String, createdAt: String?) {
        val local = dao.byClientId(clientMessageId) ?: return
        dao.putServerMessages(
            listOf(
                local.copy(
                    id = serverMessageId,
                    createdAtMs = parseServerTime(createdAt) ?: local.createdAtMs,
                    status = DmStatus.SENT,
                ),
            ),
        )
    }

    override suspend fun store(message: RealtimeMessage) = dao.putServerMessages(listOf(message.toEntity()))

    override suspend fun markPending(clientMessageId: String) = dao.setLocalStatus(clientMessageId, DmStatus.PENDING)

    override suspend fun markRead(conversationId: String, lastMessageId: String): ApiResult<Unit> =
        apiCall(json) { api.markRead(conversationId, MarkReadBody(lastMessageId)) }

    override fun sendTyping(conversationId: String, isTyping: Boolean) {
        realtime.send(
            "typing",
            JsonObject(mapOf("conversation_id" to JsonPrimitive(conversationId), "is_typing" to JsonPrimitive(isTyping))),
        )
    }

    override fun joinRoom(conversationId: String) {
        realtime.send("conv:join", JsonObject(mapOf("conversation_id" to JsonPrimitive(conversationId))))
    }

    private companion object {
        const val PAGE_SIZE = 30
        const val CACHE_KEEP = 200
        const val ACK_TIMEOUT_MS = 8_000L
    }
}

@Singleton
class RestDmSender @Inject constructor(
    private val api: FriendsApi,
    private val json: Json,
) : DmSender {
    override suspend fun send(payload: DmSendPayload): ApiResult<RealtimeMessage> = apiCall(json) {
        api.sendMessage(
            payload.conversationId,
            SendDirectMessageBody(content = payload.content, contentType = "text", clientMessageId = payload.clientMessageId),
        ).data
    }
}

@Singleton
class RoomDmSendResultStore @Inject constructor(private val dao: DmMessageDao) : DmSendResultStore {
    override suspend fun delivered(message: RealtimeMessage) = dao.putServerMessages(listOf(message.toEntity()))

    override suspend fun failed(clientMessageId: String) = dao.setLocalStatus(clientMessageId, DmStatus.FAILED)
}
