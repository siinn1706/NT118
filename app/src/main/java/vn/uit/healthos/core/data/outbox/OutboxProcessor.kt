package vn.uit.healthos.core.data.outbox

import kotlinx.coroutines.CancellationException
import vn.uit.healthos.core.network.ApiError
import vn.uit.healthos.core.network.ApiResult

/**
 * A feature's sender for one outbox `type`. Register it with
 * `@Binds @IntoMap @StringKey("<type>")` in the feature's Hilt module.
 */
fun interface OutboxHandler {
    suspend fun send(payloadJson: String): ApiResult<Unit>
}

enum class OutboxRunResult { SUCCESS, RETRY }

/**
 * Sends pending items oldest first. Retryable failures (network, 5xx, 429) stay queued and the run asks for a retry;
 * permanent failures (other 4xx, unknown type, too many attempts) are dropped so one bad item cannot block the queue.
 */
class OutboxProcessor(
    private val store: OutboxStore,
    private val handlers: Map<String, OutboxHandler>,
    private val maxAttempts: Int = 20,
    private val batchSize: Int = 50,
    private val log: (String) -> Unit = {},
) {
    suspend fun run(): OutboxRunResult {
        var needsRetry = false
        var cursor = 0L
        while (true) {
            val batch = store.pending(afterId = cursor, limit = batchSize)
            if (batch.isEmpty()) break
            for (item in batch) {
                cursor = item.id
                if (!process(item)) needsRetry = true
            }
        }
        return if (needsRetry) OutboxRunResult.RETRY else OutboxRunResult.SUCCESS
    }

    /** Returns false when the item stays queued for a later retry. */
    private suspend fun process(item: OutboxEntity): Boolean {
        val handler = handlers[item.type]
        if (handler == null) {
            log("Dropping outbox item ${item.id}: no handler for type ${item.type}")
            store.delete(item.id)
            return true
        }
        // An unexpected exception is a bug or a transient fault, never a server verdict: keep the item.
        var crashed = false
        val result = try {
            handler.send(item.payloadJson)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            crashed = true
            ApiResult.Failure(ApiError(ApiError.UNKNOWN, e.message ?: e.javaClass.simpleName))
        }
        return when (result) {
            is ApiResult.Success -> {
                store.delete(item.id)
                true
            }
            is ApiResult.Failure -> {
                val error = result.error
                val retryable = crashed || error.isRetryable
                if (!retryable || item.attempts + 1 >= maxAttempts) {
                    log("Dropping outbox item ${item.id} (${item.type}): ${error.code} ${error.message}")
                    store.delete(item.id)
                    true
                } else {
                    store.markFailed(item.id, "${error.code}: ${error.message}")
                    false
                }
            }
        }
    }
}
