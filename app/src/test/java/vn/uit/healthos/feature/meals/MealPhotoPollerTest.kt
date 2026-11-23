package vn.uit.healthos.feature.meals

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.uit.healthos.core.network.ApiResult

class MealPhotoPollerTest {

    @Test
    fun pollsUntilAnalyzedWithBackoff() = runTest {
        var now = 0L
        val delays = mutableListOf<Long>()
        val poller = MealPhotoPoller(
            initialDelayMs = 1_000L,
            maxDelayMs = 3_000L,
            timeoutMs = 60_000L,
            nowMs = { now },
            sleeper = { ms ->
                delays.add(ms)
                now += ms
            },
        )
        var calls = 0
        val outcome = poller.poll {
            calls++
            val status = if (calls < 4) "processing" else "analyzed"
            ApiResult.Success(
                AnalysisJobResponseDto(
                    jobId = "j-1",
                    status = status,
                    mealId = "m-1",
                    nutritionResult = if (status == "analyzed") NutritionResultDto(dishName = "Phở bò", calories = 480.0) else null,
                ),
            )
        }
        assertTrue(outcome is MealPollOutcome.Analyzed)
        assertEquals(listOf(1_000L, 2_000L, 3_000L), delays)
        assertEquals("Phở bò", (outcome as MealPollOutcome.Analyzed).response.nutritionResult?.dishName)
    }

    @Test
    fun returnsFailedWhenWorkerReportsFailed() = runTest {
        val poller = MealPhotoPoller(nowMs = { 0L }, sleeper = {})
        val outcome = poller.poll {
            ApiResult.Success(
                AnalysisJobResponseDto(
                    jobId = "j-2",
                    status = "failed",
                    error = MealAnalysisErrorDto(code = "AI_WORKER_FAILED", message = "Worker error"),
                ),
            )
        }
        assertTrue(outcome is MealPollOutcome.Failed)
        assertEquals("AI_WORKER_FAILED", (outcome as MealPollOutcome.Failed).code)
    }

    @Test
    fun returnsTimeoutAfterSixtySeconds() = runTest {
        var now = 0L
        val poller = MealPhotoPoller(
            initialDelayMs = 1_000L,
            maxDelayMs = 3_000L,
            timeoutMs = 60_000L,
            nowMs = { now },
            sleeper = { ms -> now += ms },
        )
        val outcome = poller.poll {
            ApiResult.Success(AnalysisJobResponseDto(jobId = "j-3", status = "processing"))
        }
        assertEquals(MealPollOutcome.Timeout, outcome)
        assertTrue(now >= 60_000L)
    }

    @Test
    fun cancellationPropagatesCleanly() = runTest {
        var cancelled = false
        val poller = MealPhotoPoller(
            nowMs = { 0L },
            sleeper = { throw CancellationException("User cancelled") },
        )
        val job = launch {
            try {
                poller.poll { ApiResult.Success(AnalysisJobResponseDto("j-4", "processing")) }
            } catch (_: CancellationException) {
                cancelled = true
            }
        }
        job.join()
        assertTrue(cancelled)
    }
}
