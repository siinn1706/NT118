package vn.uit.healthos.feature.meals

import kotlinx.coroutines.delay
import vn.uit.healthos.core.network.ApiError
import vn.uit.healthos.core.network.ApiResult

sealed interface MealPollOutcome {
    data class Analyzed(val response: AnalysisJobResponseDto) : MealPollOutcome
    /** The analysis failed with the server's [code]; [error] is set when the status request itself failed. */
    data class Failed(val code: String, val error: ApiError? = null) : MealPollOutcome
    data object Timeout : MealPollOutcome
}

/**
 * Polls `GET /v1/meals/analyze-photo/{jobId}` with 1 s → 2 s → 3 s backoff up to [timeoutMs] (60 s).
 * Respects structured coroutine cancellation.
 */
class MealPhotoPoller(
    private val initialDelayMs: Long = 1_000L,
    private val maxDelayMs: Long = 3_000L,
    private val timeoutMs: Long = 60_000L,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    private val sleeper: suspend (Long) -> Unit = { delay(it) },
) {
    suspend fun poll(
        fetchStatus: suspend () -> ApiResult<AnalysisJobResponseDto>,
    ): MealPollOutcome {
        val startedAt = nowMs()
        var waitMs = initialDelayMs
        var consecutiveErrors = 0

        while (true) {
            when (val res = fetchStatus()) {
                is ApiResult.Success -> {
                    consecutiveErrors = 0
                    val job = res.data
                    when (job.status.lowercase()) {
                        "analyzed", "completed", "done" -> return MealPollOutcome.Analyzed(job)
                        "failed", "error" -> return MealPollOutcome.Failed(code = job.error?.code ?: "ANALYSIS_FAILED")
                    }
                }
                is ApiResult.Failure -> {
                    consecutiveErrors++
                    if (!res.error.isRetryable || consecutiveErrors >= 5) {
                        return MealPollOutcome.Failed(code = res.error.code, error = res.error)
                    }
                }
            }

            val elapsed = nowMs() - startedAt
            if (elapsed >= timeoutMs) {
                return MealPollOutcome.Timeout
            }
            val remaining = timeoutMs - elapsed
            sleeper(minOf(waitMs, remaining))
            waitMs = (waitMs + 1_000L).coerceAtMost(maxDelayMs)
        }
    }
}
