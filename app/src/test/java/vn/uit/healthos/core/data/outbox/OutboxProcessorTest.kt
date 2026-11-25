package vn.uit.healthos.core.data.outbox

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.uit.healthos.core.network.ApiError
import vn.uit.healthos.core.network.ApiResult
import vn.uit.healthos.feature.safety.OUTBOX_TYPE_SOS_EMAIL

class OutboxProcessorTest {

    private class FakeStore(items: List<OutboxEntity>) : OutboxStore {
        val items = items.toMutableList()
        override suspend fun pending(afterId: Long, limit: Int) =
            items.filter { it.id > afterId }.sortedBy { it.id }.take(limit)
        override suspend fun delete(id: Long) { items.removeAll { it.id == id } }
        override suspend fun markFailed(id: Long, error: String) {
            val i = items.indexOfFirst { it.id == id }
            items[i] = items[i].copy(attempts = items[i].attempts + 1, lastError = error)
        }
        override suspend fun markHeld(id: Long, error: String) {
            val i = items.indexOfFirst { it.id == id }
            items[i] = items[i].copy(lastError = error)
        }
    }

    private fun item(id: Long, type: String = "meal.create", attempts: Int = 0, owner: String? = null) =
        OutboxEntity(
            id = id,
            type = type,
            payloadJson = """{"id":$id}""",
            createdAt = id,
            attempts = attempts,
            ownerUserId = owner,
        )

    /** The signed-in account the processor sees; tests switch it to simulate sign-out and account changes. */
    private var session: String? = "user-a"

    private fun processor(
        store: OutboxStore,
        handlers: Map<String, OutboxHandler>,
        maxAttempts: Int = 20,
        batchSize: Int = 50,
    ) = OutboxProcessor(
        store = store,
        handlers = handlers,
        sessionUserId = { session },
        maxAttempts = maxAttempts,
        batchSize = batchSize,
    )

    private val unauthorized = ApiResult.Failure(ApiError("HTTP_401", "expired", httpStatus = 401))
    private val forbidden = ApiResult.Failure(ApiError("FORBIDDEN", "no", httpStatus = 403))

    private val networkDown = ApiResult.Failure(ApiError(ApiError.NETWORK, "offline"))

    @Test
    fun `network failure keeps the item and asks for retry, next run sends it`() = runTest {
        val store = FakeStore(listOf(item(1)))
        var online = false
        val processor = processor(store, mapOf("meal.create" to OutboxHandler {
            if (online) ApiResult.Success(Unit) else networkDown
        }))

        assertEquals(OutboxRunResult.RETRY, processor.run())
        assertEquals(1, store.items.single().attempts)

        online = true
        assertEquals(OutboxRunResult.SUCCESS, processor.run())
        assertTrue(store.items.isEmpty())
    }

    @Test
    fun `items are sent oldest first`() = runTest {
        val store = FakeStore(listOf(item(3), item(1), item(2)))
        val sent = mutableListOf<String>()
        val processor = processor(store, mapOf("meal.create" to OutboxHandler {
            sent += it
            ApiResult.Success(Unit)
        }), batchSize = 2)

        processor.run()

        assertEquals(listOf("""{"id":1}""", """{"id":2}""", """{"id":3}"""), sent)
    }

    @Test
    fun `client errors, unknown types and exhausted items are dropped`() = runTest {
        val store = FakeStore(listOf(item(1), item(2, type = "unknown"), item(3, attempts = 19)))
        val processor = processor(store, mapOf("meal.create" to OutboxHandler { payload ->
            if (payload.contains("1")) {
                ApiResult.Failure(ApiError("VALIDATION_ERROR", "bad", httpStatus = 422))
            } else {
                networkDown
            }
        }), maxAttempts = 20)

        assertEquals(OutboxRunResult.SUCCESS, processor.run())
        assertTrue(store.items.isEmpty())
    }

    @Test
    fun `handler exception is retried, not lost`() = runTest {
        val store = FakeStore(listOf(item(1)))
        val processor = processor(store, mapOf("meal.create" to OutboxHandler { error("boom") }))

        assertEquals(OutboxRunResult.RETRY, processor.run())
        assertEquals(1, store.items.size)
    }

    @Test
    fun `without a session nothing is sent and nothing is dropped or counted`() = runTest {
        val store = FakeStore(listOf(item(1), item(2, type = "unknown")))
        var calls = 0
        session = null
        val processor = processor(store, mapOf("meal.create" to OutboxHandler {
            calls++
            ApiResult.Success(Unit)
        }))

        assertEquals(OutboxRunResult.HELD, processor.run())
        assertEquals(0, calls)
        assertEquals(listOf(0, 0), store.items.map { it.attempts })

        session = "user-a"
        assertEquals(OutboxRunResult.SUCCESS, processor.run())
        assertEquals(1, calls)
        assertTrue(store.items.isEmpty())
    }

    @Test
    fun `401 keeps the item without burning an attempt and stops the run`() = runTest {
        val store = FakeStore(listOf(item(1, attempts = 19), item(2)))
        val sent = mutableListOf<String>()
        val processor = processor(store, mapOf("meal.create" to OutboxHandler {
            sent += it
            // The authenticator gives up and clears the session together with the 401.
            session = null
            unauthorized
        }), maxAttempts = 20)

        assertEquals(OutboxRunResult.HELD, processor.run())
        assertEquals(listOf("""{"id":1}"""), sent)
        assertEquals(2, store.items.size)
        assertEquals(19, store.items.first().attempts)
        assertEquals("HTTP_401: expired", store.items.first().lastError)
    }

    @Test
    fun `401 while a session is still stored asks for a retry`() = runTest {
        val store = FakeStore(listOf(item(1)))
        val processor = processor(store, mapOf("meal.create" to OutboxHandler { unauthorized }))

        assertEquals(OutboxRunResult.RETRY, processor.run())
        assertEquals(0, store.items.single().attempts)
    }

    @Test
    fun `403 holds that item and the rest of the queue still goes out`() = runTest {
        val store = FakeStore(listOf(item(1), item(2)))
        val processor = processor(store, mapOf("meal.create" to OutboxHandler { payload ->
            if (payload.contains("1")) forbidden else ApiResult.Success(Unit)
        }))

        assertEquals(OutboxRunResult.HELD, processor.run())
        assertEquals(listOf(1L), store.items.map { it.id })
        assertEquals(0, store.items.single().attempts)
    }

    @Test
    fun `items queued by another account wait for that account`() = runTest {
        val store = FakeStore(listOf(item(1, type = "sos.email", owner = "user-a"), item(2, owner = null)))
        val sent = mutableListOf<String>()
        val handler = OutboxHandler {
            sent += it
            ApiResult.Success(Unit)
        }
        val processor = processor(store, mapOf("meal.create" to handler, "sos.email" to handler))

        session = "user-b"
        assertEquals(OutboxRunResult.HELD, processor.run())
        assertEquals(listOf("""{"id":2}"""), sent)
        assertEquals(listOf(1L), store.items.map { it.id })

        session = "user-a"
        assertEquals(OutboxRunResult.SUCCESS, processor.run())
        assertTrue(store.items.isEmpty())
    }

    @Test
    fun `SOS emails are kept when the account data is wiped`() {
        assertTrue(OUTBOX_TYPE_SOS_EMAIL in OutboxRetention.SESSION_INDEPENDENT_TYPES)
        assertFalse("dm.send" in OutboxRetention.SESSION_INDEPENDENT_TYPES)
    }
}
