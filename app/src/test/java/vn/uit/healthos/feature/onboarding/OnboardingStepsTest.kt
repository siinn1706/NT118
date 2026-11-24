package vn.uit.healthos.feature.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingStepsTest {

    @Test
    fun nextStopsAtTheOptionalAppearanceStep() {
        assertEquals(OnboardingSteps.APPEARANCE, OnboardingSteps.next(OnboardingSteps.CONTACTS))
        assertEquals(OnboardingSteps.APPEARANCE, OnboardingSteps.next(OnboardingSteps.APPEARANCE))
    }

    @Test
    fun previousStopsAtTheFirstStep() {
        assertEquals(OnboardingSteps.BODY, OnboardingSteps.previous(OnboardingSteps.BODY))
        assertEquals(OnboardingSteps.GOAL, OnboardingSteps.previous(OnboardingSteps.CONTACTS))
    }

    @Test
    fun onlyTheAppearanceStepCompletes() {
        assertTrue(OnboardingSteps.isLast(OnboardingSteps.APPEARANCE))
        assertFalse(OnboardingSteps.isLast(OnboardingSteps.CONTACTS))
        assertEquals(OnboardingSteps.COUNT - 1, OnboardingSteps.LAST)
    }
}
