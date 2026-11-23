package vn.uit.healthos.feature.chat

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.uit.healthos.core.network.SseEvent

class ChatStreamParserTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun assemblesOutOfOrderDeltasAndIgnoresPings() {
        val assembler = ChatStreamAssembler(json)
        assembler.consume(SseEvent("start", """{"assistant_message_id":"m-99"}"""))
        assembler.consume(SseEvent("delta", """{"text":"bạn!","seq":3}"""))
        assembler.consume(SseEvent("ping", """{"ts":123}"""))
        assembler.consume(SseEvent("delta", """{"text":"Xin ","seq":1}"""))
        assembler.consume(SseEvent("delta", """{"text":"chào ","seq":2}"""))
        assembler.consume(SseEvent("done", """{"message_id":"m-99","status":"completed"}"""))

        assertEquals("m-99", assembler.assistantMessageId)
        assertEquals("Xin chào bạn!", assembler.currentText())
        assertTrue(assembler.completed)
        assertFalse(assembler.emergencyDetected)
    }

    @Test
    fun detectsEmergencyFromServerSafetyReply() {
        val assembler = ChatStreamAssembler(json)
        assembler.consume(SseEvent("start", """{"assistant_message_id":"m-em"}"""))
        assembler.consume(
            SseEvent(
                "delta",
                """{"text":"HealthOS AI không phải dịch vụ cấp cứu và không thay thế bác sĩ. Tin nhắn của bạn có thể có dấu hiệu cần trợ giúp khẩn cấp ngay.","seq":1}""",
            ),
        )
        assembler.consume(SseEvent("done", """{"message_id":"m-em","status":"completed"}"""))

        assertTrue(assembler.emergencyDetected)
        assertTrue(assembler.completed)
    }

    @Test
    fun recordsErrorFrameWithDisplayTextWithoutMarkingCompleted() {
        val assembler = ChatStreamAssembler(json)
        assembler.consume(SseEvent("start", """{"assistant_message_id":"m-1"}"""))
        assembler.consume(SseEvent("delta", """{"text":"Một phần ","seq":1}"""))
        val frame = assembler.consume(
            SseEvent(
                "error",
                """{"message_id":"m-1","code":"STREAM_FAILED","detail":"Một phần","display_text":"Một phần"}""",
            ),
        )

        assertEquals(ChatStreamFrame.Error(messageId = "m-1", code = "STREAM_FAILED", displayText = "Một phần"), frame)
        assertFalse(assembler.completed)
        assertEquals("Một phần", assembler.error?.displayText)
        assertEquals("m-1", assembler.assistantMessageId)
        assertEquals("Một phần ", assembler.currentText())
    }

    @Test
    fun errorFrameFallsBackToDetailThenNull() {
        val fromDetail = ChatStreamAssembler.parseEvent(
            SseEvent("error", """{"code":"STREAM_FAILED","detail":"Xin lỗi, đã có lỗi."}"""),
            json,
        )
        assertEquals("Xin lỗi, đã có lỗi.", (fromDetail as ChatStreamFrame.Error).displayText)

        val blank = ChatStreamAssembler.parseEvent(SseEvent("error", """{"display_text":"  "}"""), json)
        assertNull((blank as ChatStreamFrame.Error).displayText)

        val malformed = ChatStreamAssembler.parseEvent(SseEvent("error", "not json"), json)
        assertEquals(ChatStreamFrame.Error(messageId = null, code = null, displayText = null), malformed)
    }

    @Test
    fun detectsEmergencyFromSystemMessageOrSafetyMetadata() {
        val systemMsg = ChatMessageDto(
            id = "1",
            conversationId = "c-1",
            senderKind = "ai",
            content = "Thông báo khẩn",
            contentType = "system",
        )
        assertTrue(EmergencyDetector.isEmergencyMessage(systemMsg))

        val metaMsg = ChatMessageDto(
            id = "2",
            conversationId = "c-1",
            senderKind = "ai",
            content = "Normal text",
            aiMetadata = buildJsonObject { put("safety_blocked", JsonPrimitive(true)) },
        )
        assertTrue(EmergencyDetector.isEmergencyMessage(metaMsg))
    }
}
