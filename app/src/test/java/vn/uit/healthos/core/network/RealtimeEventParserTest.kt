package vn.uit.healthos.core.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RealtimeEventParserTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val parser = RealtimeEventParser(json)

    @Test
    fun `msg new and chat message sent both decode the message`() {
        val payload = """
            {"id":"m1","conversation_id":"c1","sender_id":"u2","client_message_id":"k1","content":"Chào",
             "content_type":"text","reactions":[],"created_at":"2026-11-25T03:00:00.123456+00:00"}
        """.trimIndent()
        listOf("msg:new", "chat.message.sent").forEach { name ->
            val event = parser.parse("""{"event":"$name","payload":$payload,"timestamp":"t"}""")
            val message = (event as RealtimeEvent.MessageNew).message
            assertEquals("m1", message.id)
            assertEquals("c1", message.conversationId)
            assertEquals("u2", message.senderId)
            assertEquals("k1", message.clientMessageId)
            assertEquals("Chào", message.content)
        }
    }

    @Test
    fun `msg ack carries the client and server ids`() {
        val event = parser.parse(
            """{"event":"msg:ack","payload":{"client_message_id":"k1","server_message_id":"m9","created_at":"2026-11-25T03:00:00+00:00"}}""",
        )
        assertEquals(RealtimeEvent.MessageAck("k1", "m9", "2026-11-25T03:00:00+00:00"), event)
    }

    @Test
    fun `typing, read receipts and presence are typed`() {
        assertEquals(
            RealtimeEvent.Typing("c1", "u2", false),
            parser.parse("""{"event":"chat.typing","payload":{"conversation_id":"c1","user_id":"u2","is_typing":false}}"""),
        )
        assertEquals(
            RealtimeEvent.MessagesRead("c1", "u2", "m3"),
            parser.parse("""{"event":"msg:read","payload":{"conversation_id":"c1","user_id":"u2","last_read_message_id":"m3"}}"""),
        )
        assertEquals(
            RealtimeEvent.Presence("u2", true),
            parser.parse("""{"event":"user.status","payload":{"user_id":"u2","is_online":true,"last_seen_at":null}}"""),
        )
    }

    @Test
    fun `friend events keep their payload for the feature to decode`() {
        val received = parser.parse(
            """{"event":"friend.request.received","payload":{"id":"r1","status":"pending","direction":"incoming"}}""",
        ) as RealtimeEvent.FriendRequestReceived
        assertEquals("r1", received.request["id"]?.jsonPrimitive?.content)

        val accepted = parser.parse(
            """{"event":"friend.request.accepted","payload":{"request_id":"r1","friend":{"since":"x"}}}""",
        ) as RealtimeEvent.FriendRequestAccepted
        assertEquals("r1", accepted.requestId)
        assertEquals("x", accepted.friend.jsonObject["since"]?.jsonPrimitive?.content)
    }

    @Test
    fun `ping, error and unknown events`() {
        assertEquals(RealtimeEvent.Ping, parser.parse("""{"event":"ping","payload":{},"timestamp":"t"}"""))
        assertEquals(
            RealtimeEvent.Error("AUTH_INVALID_TOKEN", "Invalid or expired ticket."),
            parser.parse("""{"event":"error","payload":{"code":"AUTH_INVALID_TOKEN","message":"Invalid or expired ticket."}}"""),
        )
        val other = parser.parse("""{"event":"conv:joined","payload":{"conversation_id":"c1"}}""")
        assertTrue(other is RealtimeEvent.Other && other.event == "conv:joined")
    }

    @Test
    fun `malformed frames give null instead of throwing`() {
        assertNull(parser.parse("not json"))
        assertNull(parser.parse("[1,2]"))
        assertNull(parser.parse("""{"payload":{}}"""))
        assertNull(parser.parse("""{"event":"msg:new","payload":{"content":"missing ids"}}"""))
        assertNull(parser.parse("""{"event":"msg:ack","payload":{"client_message_id":"k1"}}"""))
        assertNull(parser.parse("""{"event":"typing","payload":{"user_id":"u2"}}"""))
        assertNull(parser.parse("""{"event":"friend.request.accepted","payload":{"request_id":"r1"}}"""))
    }

    @Test
    fun `client frames match the backend protocol`() {
        val auth = json.parseToJsonElement(RealtimeFrames.auth(json, "ticket-1")).jsonObject
        assertEquals("auth", auth["type"]?.jsonPrimitive?.content)
        assertEquals("ticket-1", auth["ticket"]?.jsonPrimitive?.content)

        val send = json.parseToJsonElement(
            RealtimeFrames.event(json, "msg:send", JsonObject(mapOf("conversation_id" to JsonPrimitive("c1")))),
        ).jsonObject
        assertEquals("msg:send", send["event"]?.jsonPrimitive?.content)
        assertEquals("c1", send["payload"]?.jsonObject?.get("conversation_id")?.jsonPrimitive?.content)

        assertEquals("pong", json.parseToJsonElement(RealtimeFrames.pong(json)).jsonObject["event"]?.jsonPrimitive?.content)
    }
}
