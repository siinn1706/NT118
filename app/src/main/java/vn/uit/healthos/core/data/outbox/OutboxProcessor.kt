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

enum class OutboxRunResult {
    /** Nothing left that can be sent now. */
    SUCCESS,

    /** A transient failure: run again later with backoff. */
    RETRY,

    /** Items wait for a session (signed out, expired, or refused with 401/403); run again after the next sign-in. */
    HELD,
}

/**
 * Sends pending items oldest first.
 * - Retryable failures (network, 5xx, 429) stay queued, count an attempt and ask for a retry.
 * - Session failures never drop or count against an item: with no signed-in account nothing is sent, a 401 stops the
 *   run (every later request would fail the same way), and a 403 holds that item until the next run. Held items wait
 *   for the next sign-in ([OutboxRepository.scheduleSync]) instead of spinning the worker.
 * - Items queued by another account are skipped until that account signs in again.
 * - Permanent failures (other 4xx, unknown type, too many attempts) are dropped so one bad item cannot block the queue.
 */
class OutboxProcessor(
    private val store: OutboxStore,
    private val handlers: Map<String, OutboxHandler>,
    private val sessionUserId: () -> String?,
    private val maxAttempts: Int = 20,
    private val batchSize: Int = 50,
    private val log: (String) -> Unit = {},
) {
    private enum class Outcome { DONE, RETRY, HELD, STOP }

    suspend fun run(): OutboxRunResult {
        var needsRetry = false
        var held = false
        var cursor = 0L
        while (true) {
            val batch = store.pending(afterId = cursor, limit = batchSize)
            if (batch.isEmpty()) break
            for (item in batch) {
                cursor = item.id
                when (process(item)) {
                    Outcome.DONE -> Unit
                    Outcome.RETRY -> needsRetry = true
                    Outcome.HELD -> held = true
                    Outcome.STOP -> return if (sessionUserId() == null) OutboxRunResult.HELD else OutboxRunResult.RETRY
                }
            }
        }
        return when {
            needsRetry -> OutboxRunResult.RETRY
            held -> OutboxRunResult.HELD
            else -> OutboxRunResult.SUCCESS
        }
    }

    private suspend fun process(item: OutboxEntity): Outcome {
        val userId = sessionUserId() ?: return Outcome.STOP
        if (item.ownerUserId != null && item.ownerUserId != userId) return Outcome.HELD
        val handler = handlers[item.type]
        if (handler == null) {
            log("Dropping outbox item ${item.id}: no handler for type ${item.type}")
            store.delete(item.id)
            return Outcome.DONE
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
                Outcome.DONE
            }
            is ApiResult.Failure -> onFailure(item, result.error, crashed)
        }
    }

    private suspend fun onFailure(item: OutboxEntity, error: ApiError, crashed: Boolean): Outcome {
        val reason = "${error.code}: ${error.message}"
        when (error.httpStatus) {
            HTTP_UNAUTHORIZED -> {
                store.markHeld(item.id, reason)
                return Outcome.STOP
            }
            HTTP_FORBIDDEN -> {
                store.markHeld(item.id, reason)
                return Outcome.HELD
            }
        }
        val retryable = crashed || error.isRetryable
        return if (!retryable || item.attempts + 1 >= maxAttempts) {
            log("Dropping outbox item ${item.id} (${item.type}): $reason")
            store.delete(item.id)
            Outcome.DONE
        } else {
            store.markFailed(item.id, reason)
            Outcome.RETRY
        }
    }

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
    }
}
