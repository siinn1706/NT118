package vn.uit.healthos.feature.steps

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StepServicePolicyTest {

    @Test
    fun runsOnlyWhenPermittedAndSignedIn() {
        assertTrue(StepServicePolicy.shouldRun(activityRecognitionGranted = true, signedIn = true))
        assertFalse(StepServicePolicy.shouldRun(activityRecognitionGranted = false, signedIn = true))
        assertFalse(StepServicePolicy.shouldRun(activityRecognitionGranted = true, signedIn = false))
    }

    @Test
    fun stepCountRestartsForADifferentAccountOrTheFirstSignIn() {
        assertTrue(StepServicePolicy.shouldResetStepsOnSignIn(previousOwner = "user-a", userId = "user-b"))
        assertTrue(StepServicePolicy.shouldResetStepsOnSignIn(previousOwner = null, userId = "user-a"))
        assertFalse(StepServicePolicy.shouldResetStepsOnSignIn(previousOwner = "user-a", userId = "user-a"))
    }

    @Test
    fun firstReadingAlwaysRefreshesWidgets() {
        assertTrue(StepServicePolicy.shouldRefreshWidgets(lastRefreshAtMs = null, lastRefreshSteps = 0, nowMs = 0, steps = 1))
    }

    @Test
    fun widgetRefreshIsThrottledByTimeAndSteps() {
        val last = 1_000_000L
        assertFalse(StepServicePolicy.shouldRefreshWidgets(last, 1000, last + 60_000, 1100))
        assertTrue(
            StepServicePolicy.shouldRefreshWidgets(last, 1000, last + StepServicePolicy.WIDGET_REFRESH_INTERVAL_MS, 1001),
        )
        assertTrue(
            StepServicePolicy.shouldRefreshWidgets(last, 1000, last + 1, 1000 + StepServicePolicy.WIDGET_REFRESH_STEPS),
        )
    }

    @Test
    fun lowerCountRefreshesAtOnce() {
        assertTrue(StepServicePolicy.shouldRefreshWidgets(1_000L, 5000, 1_001L, 0))
    }
}
