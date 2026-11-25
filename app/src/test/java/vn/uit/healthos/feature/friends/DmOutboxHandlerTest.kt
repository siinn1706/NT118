package vn.uit.healthos.feature.friends

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.uit.healthos.core.data.outbox.OutboxEntity
import vn.uit.healthos.core.data.outbox.OutboxHandler
import vn.uit.healthos.core.data.outbox.OutboxProcessor
import vn.uit.healthos.core.data.outbox.OutboxRunResult
import vn.uit.healthos.core.data.outbox.OutboxStore
import vn.uit.healthos.core.network.ApiError
import vn.uit.healthos.core.network.ApiResult
import vn.uit.healthos.core.network.RealtimeMessage

class DmOutboxHandlerTest {

    private val json = Json { ignoreUnknownKeys = true }

    private class RecordingResults : DmSendResultStore {
        val delivered = mutableListOf<RealtimeMessage>()
        val failed = mutableListOf<String>()
        override suspend fun delivered(message: RealtimeMessage) {
            delivered += message
        }
        override suspend fun failed(clientMessageId: String) {
            failed += clientMessageId
        }
    }

    private class FakeStore(items: List<OutboxEntity>) : OutboxStore {
        val items = items.toMutableList()
        override suspend fun pending(afterId: Long, limit: Int) = items.filter { it.id > afterId }.take(limit)
        override suspend fun delete(id: Long) {
            items.removeAll { it.id == id }
        }
        override suspend fun markFailed(id: Long, error: String) {
            val i = items.indexOfFirst { it.id == id }
            items[i] = items[i].copy(attempts = items[i].attempts + 1)
        }
        override suspend fun markHeld(id: Long, error: String) = Unit
    }

    private val payload = DmSendPayload(conversationId = "c1", content = "Chào bạn", clientMessageId = "k1")
    private val payloadJson = json.encodeToString(DmSendPayload.serializer(), payload)
    private val serverCopy = RealtimeMessage(
        id = "m1",
        conversationId = "c1",
        senderId = "me",
        clientMessageId = "k1",
        content = "Chào bạn",
        createdAt = "2026-11-25T03:00:00+00:00",
    )

    @Test
    fun `delivers over REST with the client message id and stores the server copy`() = runTest {
        val sent = mutableListOf<DmSendPayload>()
        val results = RecordingResults()
        val handler = DmOutboxHandler({ sent += it; ApiResult.Success(serverCopy) }, results, json)

        assertEquals(ApiResult.Success(Unit), handler.send(payloadJson))
        assertEquals(listOf(payload), sent)
        assertEquals(listOf(serverCopy), results.delivered)
    }

    @Test
    fun `network failure stays queued without marking the message failed`() = runTest {
        val results = RecordingResults()
        val handler = DmOutboxHandler({ ApiResult.Failure(ApiError(ApiError.NETWORK, "offline")) }, results, json)

        val result = handler.send(payloadJson) as ApiResult.Failure
        assertTrue(result.error.isRetryable)
        assertTrue(results.failed.isEmpty())
    }

    @Test
    fun `no longer a member is final and marks the message failed`() = runTest {
        val results = RecordingResults()
        val forbidden = ApiError("CHAT_FORBIDDEN", "not a member", httpStatus = 403)
        val handler = DmOutboxHandler({ ApiResult.Failure(forbidden) }, results, json)

        val result = handler.send(payloadJson) as ApiResult.Failure
        assertNull(result.error.httpStatus)
        assertEquals(listOf("k1"), results.failed)

        // The outbox drops it instead of holding it for a new session.
        val store = FakeStore(listOf(OutboxEntity(id = 1, type = OUTBOX_TYPE_DM_SEND, payloadJson = payloadJson, createdAt = 0)))
        val processor = OutboxProcessor(store, mapOf(OUTBOX_TYPE_DM_SEND to handler as OutboxHandler), sessionUserId = { "me" })
        assertEquals(OutboxRunResult.SUCCESS, processor.run())
        assertTrue(store.items.isEmpty())
    }

    @Test
    fun `expired session is held, not failed`() = runTest {
        val results = RecordingResults()
        val handler = DmOutboxHandler(
            { ApiResult.Failure(ApiError("HTTP_401", "expired", httpStatus = 401)) },
            results,
            json,
        )
        val store = FakeStore(listOf(OutboxEntity(id = 1, type = OUTBOX_TYPE_DM_SEND, payloadJson = payloadJson, createdAt = 0)))
        val processor = OutboxProcessor(store, mapOf(OUTBOX_TYPE_DM_SEND to handler as OutboxHandler), sessionUserId = { "me" })

        assertEquals(OutboxRunResult.RETRY, processor.run())
        assertEquals(1, store.items.size)
        assertTrue(results.failed.isEmpty())
    }

    @Test
    fun `a send the outbox gives up on is marked failed so it can be retried`() = runTest {
        val results = RecordingResults()
        val handler = DmOutboxHandler({ ApiResult.Failure(ApiError(ApiError.NETWORK, "offline")) }, results, json)
        val store = FakeStore(
            listOf(OutboxEntity(id = 1, type = OUTBOX_TYPE_DM_SEND, payloadJson = payloadJson, createdAt = 0, attempts = 19)),
        )
        val processor = OutboxProcessor(
            store,
            mapOf(OUTBOX_TYPE_DM_SEND to handler as OutboxHandler),
            sessionUserId = { "me" },
            maxAttempts = 20,
        )

        assertEquals(OutboxRunResult.SUCCESS, processor.run())
        assertTrue(store.items.isEmpty())
        assertEquals(listOf("k1"), results.failed)
    }

    @Test
    fun `pending sends with no outbox item are queued again`() = runTest {
        fun row(clientId: String?, status: String = DmStatus.PENDING) = DmMessageEntity(
            id = "local:$clientId",
            conversationId = "c1",
            senderId = "me",
            content = "tin $clientId",
            clientMessageId = clientId,
            createdAtMs = 0,
            status = status,
        )
        val queued = mutableListOf<DmSendPayload>()

        requeueStrandedSends(
            pending = listOf(row("stranded"), row("in-outbox"), row(null), row("failed", DmStatus.FAILED)),
            isQueued = { it == "in-outbox" },
            enqueue = { queued += it },
        )

        assertEquals(listOf(DmSendPayload("c1", "tin stranded", "stranded")), queued)
    }

    @Test
    fun `unreadable payload is reported as a permanent error`() = runTest {
        val handler = DmOutboxHandler({ error("must not be called") }, RecordingResults(), json)
        val result = handler.send("{not json") as ApiResult.Failure
        assertEquals(ApiError.PARSE, result.error.code)
    }

    @Test
    fun `server times are parsed with or without an offset`() {
        assertEquals(1_795_575_600_000L, parseServerTime("2026-11-25T03:00:00+00:00"))
        assertEquals(1_795_575_600_123L, parseServerTime("2026-11-25T03:00:00.123456+00:00"))
        assertEquals(1_795_575_600_000L, parseServerTime("2026-11-25T03:00:00Z"))
        assertNull(parseServerTime("yesterday"))
        assertNull(parseServerTime(null))
    }
}
