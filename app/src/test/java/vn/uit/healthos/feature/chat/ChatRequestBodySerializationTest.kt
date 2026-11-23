package vn.uit.healthos.feature.chat

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ChatRequestBodySerializationTest {

    /** Same configuration as `NetworkModule.provideJson()`; `encodeDefaults` stays false. */
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    private fun encode(body: QuickReminderCreateBody): JsonObject =
        json.parseToJsonElement(json.encodeToString(QuickReminderCreateBody.serializer(), body)).jsonObject

    @Test
    fun quickReminderAlwaysSendsTypeAndRepeat() {
        val encoded = encode(
            QuickReminderCreateBody(type = "medicine", title = "Uống thuốc", time = "08:00", repeat = "once"),
        )

        assertEquals("medicine", encoded["type"]?.jsonPrimitive?.content)
        assertEquals("once", encoded["repeat"]?.jsonPrimitive?.content)
        assertEquals("Uống thuốc", encoded["title"]?.jsonPrimitive?.content)
        assertEquals("08:00", encoded["time"]?.jsonPrimitive?.content)
        assertFalse(encoded.containsKey("note"))
    }

    @Test
    fun sendMessageAlwaysSendsContentTypeAndLocale() {
        val body = SendMessageBody(content = "Xin chào", contentType = "text", clientMessageId = "c-1", locale = "vi")

        val encoded = json.parseToJsonElement(json.encodeToString(SendMessageBody.serializer(), body)).jsonObject

        assertEquals("text", encoded["content_type"]?.jsonPrimitive?.content)
        assertEquals("vi", encoded["locale"]?.jsonPrimitive?.content)
        assertEquals("c-1", encoded["client_message_id"]?.jsonPrimitive?.content)
        assertFalse(encoded.containsKey("attachments"))
    }
}
