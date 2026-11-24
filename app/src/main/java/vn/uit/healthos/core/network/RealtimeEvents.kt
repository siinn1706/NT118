package vn.uit.healthos.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/** Chat message as the backend sends it (`MessageDTO`), over REST and over the socket. */
@Serializable
data class RealtimeMessage(
    val id: String,
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("sender_id") val senderId: String? = null,
    @SerialName("sender_display_name") val senderDisplayName: String? = null,
    @SerialName("sender_kind") val senderKind: String = "user",
    @SerialName("client_message_id") val clientMessageId: String? = null,
    val content: String = "",
    @SerialName("content_type") val contentType: String = "text",
    @SerialName("is_recalled") val isRecalled: Boolean = false,
    @SerialName("created_at") val createdAt: String = "",
)

/** Typed server frames of the main `/ws` socket. Payloads owned by a feature stay as JSON for it to decode. */
sealed interface RealtimeEvent {
    /** `msg:new` (room broadcast) and `chat.message.sent` (room and per-member fanout); deduplicate by message id. */
    data class MessageNew(val message: RealtimeMessage) : RealtimeEvent

    /** `msg:ack`: the server stored a message this client sent over the socket. */
    data class MessageAck(
        val clientMessageId: String?,
        val serverMessageId: String,
        val createdAt: String?,
    ) : RealtimeEvent

    data class Typing(val conversationId: String, val userId: String, val isTyping: Boolean) : RealtimeEvent

    data class MessagesRead(
        val conversationId: String,
        val userId: String,
        val lastReadMessageId: String,
    ) : RealtimeEvent

    /** `user.status`, sent to the conversation rooms the user shares with this client. */
    data class Presence(val userId: String, val isOnline: Boolean) : RealtimeEvent

    data class FriendRequestReceived(val request: JsonObject) : RealtimeEvent

    data class FriendRequestAccepted(val requestId: String, val friend: JsonObject) : RealtimeEvent

    data class ConversationUpdated(val conversation: JsonObject) : RealtimeEvent

    data class NotificationCreated(val notification: JsonObject) : RealtimeEvent

    data class Error(val code: String, val message: String) : RealtimeEvent

    /** Server heartbeat; the socket answers it with `pong` itself. */
    data object Ping : RealtimeEvent

    data class Other(val event: String, val payload: JsonObject) : RealtimeEvent
}

/** Turns raw socket text into a [RealtimeEvent]; malformed frames give null and never throw. */
class RealtimeEventParser(private val json: Json) {

    fun parse(text: String): RealtimeEvent? {
        val frame = try {
            json.parseToJsonElement(text) as? JsonObject
        } catch (e: SerializationException) {
            null
        } ?: return null
        val event = frame.string("event") ?: return null
        val payload = frame["payload"] as? JsonObject ?: JsonObject(emptyMap())
        return try {
            toEvent(event, payload)
        } catch (e: SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private fun toEvent(event: String, payload: JsonObject): RealtimeEvent? = when (event) {
        "ping" -> RealtimeEvent.Ping
        "msg:new", "chat.message.sent" ->
            RealtimeEvent.MessageNew(json.decodeFromJsonElement(RealtimeMessage.serializer(), payload))
        "msg:ack" -> payload.string("server_message_id")?.let {
            RealtimeEvent.MessageAck(
                clientMessageId = payload.string("client_message_id"),
                serverMessageId = it,
                createdAt = payload.string("created_at"),
            )
        }
        "typing", "chat.typing" -> {
            val conversationId = payload.string("conversation_id")
            val userId = payload.string("user_id")
            if (conversationId == null || userId == null) {
                null
            } else {
                RealtimeEvent.Typing(conversationId, userId, payload.boolean("is_typing") ?: true)
            }
        }
        "msg:read", "chat.message.read" -> {
            val conversationId = payload.string("conversation_id")
            val userId = payload.string("user_id")
            val last = payload.string("last_read_message_id")
            if (conversationId == null || userId == null || last == null) {
                null
            } else {
                RealtimeEvent.MessagesRead(conversationId, userId, last)
            }
        }
        "user.status" -> payload.string("user_id")?.let {
            RealtimeEvent.Presence(it, payload.boolean("is_online") ?: false)
        }
        "friend.request.received" -> RealtimeEvent.FriendRequestReceived(payload)
        "friend.request.accepted" -> {
            val requestId = payload.string("request_id")
            val friend = payload["friend"] as? JsonObject
            if (requestId == null || friend == null) null else RealtimeEvent.FriendRequestAccepted(requestId, friend)
        }
        "conversation.updated" -> RealtimeEvent.ConversationUpdated(payload)
        "notification.created" -> RealtimeEvent.NotificationCreated(payload)
        "error" -> RealtimeEvent.Error(
            code = payload.string("code") ?: ApiError.UNKNOWN,
            message = payload.string("message").orEmpty(),
        )
        else -> RealtimeEvent.Other(event, payload)
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.boolean(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull
}

/** Builds the frames this client sends. */
internal object RealtimeFrames {
    /** First frame after the socket opens (the backend's post-connect auth; the ticket never goes in the URL). */
    fun auth(json: Json, ticket: String): String = json.encodeToString(
        JsonObject.serializer(),
        JsonObject(mapOf("type" to JsonPrimitive("auth"), "ticket" to JsonPrimitive(ticket))),
    )

    fun event(json: Json, event: String, payload: JsonObject): String = json.encodeToString(
        JsonObject.serializer(),
        JsonObject(mapOf("event" to JsonPrimitive(event), "payload" to payload)),
    )

    fun pong(json: Json): String = event(json, "pong", JsonObject(emptyMap()))
}
