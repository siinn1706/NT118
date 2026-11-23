package vn.uit.healthos.feature.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import vn.uit.healthos.feature.onboarding.ActivityLevel
import vn.uit.healthos.feature.onboarding.BiologicalSex
import vn.uit.healthos.feature.onboarding.GoalDirection
import vn.uit.healthos.feature.onboarding.TdeeCalculator
import java.time.LocalDate

class NutritionTargetEstimateTest {

    private val today = LocalDate.of(2026, 11, 23)
    private val profile = UserProfileDto(
        id = "u1",
        email = "a@b.c",
        displayName = "A",
        dateOfBirth = "2004-05-10",
        gender = "female",
        heightCm = 160.0,
        weightKg = 60.0,
    )

    @Test
    fun `matches the onboarding formula with the derived goal`() {
        val targets = estimateNutritionTargets(profile, 55.0, ActivityLevel.LIGHT, today)!!
        val expected = TdeeCalculator.calculate(
            BiologicalSex.FEMALE, 60.0, 160.0, 22, ActivityLevel.LIGHT, GoalDirection.LOSE,
        )
        assertEquals(GoalDirection.LOSE, targets.goal)
        assertEquals(expected.targetCalories, targets.dailyCalories)
    }

    @Test
    fun `goal follows the weight goal with a tolerance`() {
        assertEquals(GoalDirection.GAIN, estimateNutritionTargets(profile, 65.0, ActivityLevel.MODERATE, today)!!.goal)
        assertEquals(GoalDirection.MAINTAIN, estimateNutritionTargets(profile, 60.5, ActivityLevel.MODERATE, today)!!.goal)
        assertEquals(GoalDirection.MAINTAIN, estimateNutritionTargets(profile, null, ActivityLevel.MODERATE, today)!!.goal)
    }

    @Test
    fun `missing body metrics give no estimate`() {
        assertNull(estimateNutritionTargets(profile.copy(weightKg = null), 55.0, ActivityLevel.MODERATE, today))
        assertNull(estimateNutritionTargets(profile.copy(dateOfBirth = "not-a-date"), 55.0, ActivityLevel.MODERATE, today))
    }
}
