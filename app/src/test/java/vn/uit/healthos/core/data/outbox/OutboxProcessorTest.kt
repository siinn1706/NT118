package vn.uit.healthos.core.data.outbox

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.uit.healthos.core.network.ApiError
import vn.uit.healthos.core.network.ApiResult

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
    }

    private fun item(id: Long, type: String = "meal.create", attempts: Int = 0) =
        OutboxEntity(id = id, type = type, payloadJson = """{"id":$id}""", createdAt = id, attempts = attempts)

    private val networkDown = ApiResult.Failure(ApiError(ApiError.NETWORK, "offline"))

    @Test
    fun `network failure keeps the item and asks for retry, next run sends it`() = runTest {
        val store = FakeStore(listOf(item(1)))
        var online = false
        val processor = OutboxProcessor(store, mapOf("meal.create" to OutboxHandler {
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
        val processor = OutboxProcessor(store, mapOf("meal.create" to OutboxHandler {
            sent += it
            ApiResult.Success(Unit)
        }), batchSize = 2)

        processor.run()

        assertEquals(listOf("""{"id":1}""", """{"id":2}""", """{"id":3}"""), sent)
    }

    @Test
    fun `client errors, unknown types and exhausted items are dropped`() = runTest {
        val store = FakeStore(listOf(item(1), item(2, type = "unknown"), item(3, attempts = 19)))
        val processor = OutboxProcessor(store, mapOf("meal.create" to OutboxHandler { payload ->
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
        val processor = OutboxProcessor(store, mapOf("meal.create" to OutboxHandler { error("boom") }))

        assertEquals(OutboxRunResult.RETRY, processor.run())
        assertEquals(1, store.items.size)
    }
}
