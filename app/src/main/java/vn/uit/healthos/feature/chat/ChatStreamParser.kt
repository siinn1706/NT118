package vn.uit.healthos.feature.chat

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import vn.uit.healthos.core.network.SseEvent
import java.util.TreeMap

sealed interface ChatStreamFrame {
    data class Start(val assistantMessageId: String, val userMessageId: String?) : ChatStreamFrame
    data class Delta(val seq: Int, val text: String) : ChatStreamFrame
    data object Ping : ChatStreamFrame
    data class Done(val messageId: String?, val status: String, val emergency: Boolean) : ChatStreamFrame
    data class Aborted(val messageId: String?) : ChatStreamFrame
    /** Server-side stream failure; [displayText] is the user-facing text the server persisted for the reply. */
    data class Error(val messageId: String?, val code: String?, val displayText: String?) : ChatStreamFrame
}

/**
 * Parses HealthOS SSE frames (`start`, `delta`, `ping`, `done`, `aborted`, `error`) and
 * reassembles deltas ordered by `seq` even if frames arrive out of order.
 */
class ChatStreamAssembler(private val json: Json) {
    private val chunks = TreeMap<Int, String>()
    private var nextFallbackSeq = 1
    var assistantMessageId: String? = null
        private set
    var completed: Boolean = false
        private set
    var error: ChatStreamFrame.Error? = null
        private set
    var emergencyDetected: Boolean = false
        private set

    fun currentText(): String = chunks.values.joinToString(separator = "")

    fun consume(event: SseEvent): ChatStreamFrame {
        val frame = parseEvent(event, json)
        when (frame) {
            is ChatStreamFrame.Start -> {
                assistantMessageId = frame.assistantMessageId
            }
            is ChatStreamFrame.Delta -> {
                val seq = if (frame.seq > 0) frame.seq else nextFallbackSeq++
                chunks[seq] = frame.text
                if (EmergencyDetector.isEmergencyText(currentText())) {
                    emergencyDetected = true
                }
            }
            ChatStreamFrame.Ping -> Unit
            is ChatStreamFrame.Done -> {
                if (frame.messageId != null) assistantMessageId = frame.messageId
                if (frame.emergency || EmergencyDetector.isEmergencyText(currentText())) {
                    emergencyDetected = true
                }
                completed = true
            }
            is ChatStreamFrame.Aborted -> {
                if (frame.messageId != null) assistantMessageId = frame.messageId
                completed = true
            }
            is ChatStreamFrame.Error -> {
                if (frame.messageId != null) assistantMessageId = frame.messageId
                error = frame
            }
        }
        return frame
    }

    companion object {
        fun parseEvent(event: SseEvent, json: Json): ChatStreamFrame {
            val type = event.type.lowercase()
            if (type == "ping" || type == "heartbeat") return ChatStreamFrame.Ping

            val obj = runCatching { json.parseToJsonElement(event.data).jsonObject }.getOrNull()
            return when (type) {
                "start" -> ChatStreamFrame.Start(
                    assistantMessageId = obj?.get("assistant_message_id")?.jsonPrimitive?.contentOrNull.orEmpty(),
                    userMessageId = obj?.get("user_message_id")?.jsonPrimitive?.contentOrNull,
                )
                "delta" -> ChatStreamFrame.Delta(
                    seq = obj?.get("seq")?.jsonPrimitive?.intOrNull ?: 0,
                    text = obj?.get("text")?.jsonPrimitive?.contentOrNull
                        ?: obj?.get("delta")?.jsonPrimitive?.contentOrNull
                        ?: "",
                )
                "done" -> ChatStreamFrame.Done(
                    messageId = obj?.get("message_id")?.jsonPrimitive?.contentOrNull,
                    status = obj?.get("status")?.jsonPrimitive?.contentOrNull ?: "completed",
                    emergency = EmergencyDetector.isEmergencyMetadata(obj),
                )
                "aborted" -> ChatStreamFrame.Aborted(
                    messageId = obj?.get("message_id")?.jsonPrimitive?.contentOrNull,
                )
                "error" -> ChatStreamFrame.Error(
                    messageId = obj?.get("message_id")?.jsonPrimitive?.contentOrNull,
                    code = obj?.get("code")?.jsonPrimitive?.contentOrNull,
                    displayText = (
                        obj?.get("display_text")?.jsonPrimitive?.contentOrNull
                            ?: obj?.get("detail")?.jsonPrimitive?.contentOrNull
                        )?.takeIf { it.isNotBlank() },
                )
                else -> {
                    val deltaText = obj?.get("text")?.jsonPrimitive?.contentOrNull
                        ?: obj?.get("delta")?.jsonPrimitive?.contentOrNull
                    if (deltaText != null) {
                        ChatStreamFrame.Delta(
                            seq = obj?.get("seq")?.jsonPrimitive?.intOrNull ?: 0,
                            text = deltaText,
                        )
                    } else {
                        ChatStreamFrame.Ping
                    }
                }
            }
        }
    }
}

/**
 * Detects emergency short-circuit replies emitted by HealthOS `medical_safety.py`
 * (`content_type = "system"`, emergency reply markers, or `safety_blocked` / `emergency` metadata).
 */
object EmergencyDetector {
    private val EMERGENCY_PHRASES = listOf(
        "không phải dịch vụ cấp cứu",
        "cần trợ giúp khẩn cấp ngay",
        "gọi số cấp cứu",
        "is not emergency care",
        "need urgent help now",
    )

    fun isEmergencyText(text: String): Boolean {
        val lower = text.lowercase()
        return EMERGENCY_PHRASES.any { lower.contains(it) }
    }

    fun isEmergencyMetadata(meta: JsonObject?): Boolean {
        if (meta == null) return false
        return meta["emergency"]?.jsonPrimitive?.booleanOrNull == true ||
            meta["safety_blocked"]?.jsonPrimitive?.booleanOrNull == true ||
            meta["safety_level"]?.jsonPrimitive?.contentOrNull?.equals("emergency", ignoreCase = true) == true
    }

    fun isEmergencyMessage(message: ChatMessageDto): Boolean =
        message.contentType.equals("system", ignoreCase = true) ||
            isEmergencyMetadata(message.aiMetadata) ||
            isEmergencyText(message.content)
}
