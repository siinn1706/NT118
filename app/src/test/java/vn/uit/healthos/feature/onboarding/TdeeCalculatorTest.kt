package vn.uit.healthos.feature.onboarding

import org.junit.Assert.assertEquals
import org.junit.Test

class TdeeCalculatorTest {

    @Test
    fun maleModerateMaintainMatchesMifflinStJeor() {
        // BMR = 10*70 + 6.25*175 - 5*25 + 5 = 700 + 1093.75 - 125 + 5 = 1673.75 -> 1674
        // TDEE = 1673.75 * 1.55 = 2594.3125 -> 2594
        // BMI = 70 / 1.75^2 = 22.857 -> 22.9
        val result = TdeeCalculator.calculate(
            sex = BiologicalSex.MALE,
            weightKg = 70.0,
            heightCm = 175.0,
            ageYears = 25,
            activity = ActivityLevel.MODERATE,
            goal = GoalDirection.MAINTAIN,
        )
        assertEquals(1674, result.bmr)
        assertEquals(2594, result.maintenanceCalories)
        assertEquals(0, result.goalAdjustment)
        assertEquals(2594, result.targetCalories)
        assertEquals(22.9, result.bmi, 0.01)
    }

    @Test
    fun femaleLightLoseAppliesMinus500Kcal() {
        // BMR = 10*58 + 6.25*162 - 5*28 - 161 = 580 + 1012.5 - 140 - 161 = 1291.5 -> 1292
        // TDEE = 1291.5 * 1.375 = 1775.8125 -> 1776
        // Target = 1776 - 500 = 1276
        val result = TdeeCalculator.calculate(
            sex = BiologicalSex.FEMALE,
            weightKg = 58.0,
            heightCm = 162.0,
            ageYears = 28,
            activity = ActivityLevel.LIGHT,
            goal = GoalDirection.LOSE,
        )
        assertEquals(1292, result.bmr)
        assertEquals(1776, result.maintenanceCalories)
        assertEquals(-500, result.goalAdjustment)
        assertEquals(1276, result.targetCalories)
    }

    @Test
    fun gainGoalAppliesPlus300Kcal() {
        // BMR = 10*60 + 6.25*170 - 5*22 + 5 = 1557.5 -> 1558
        // TDEE = 1557.5 * 1.725 = 2686.6875 -> 2687
        // Target = 2687 + 300 = 2987
        val result = TdeeCalculator.calculate(
            sex = BiologicalSex.MALE,
            weightKg = 60.0,
            heightCm = 170.0,
            ageYears = 22,
            activity = ActivityLevel.ACTIVE,
            goal = GoalDirection.GAIN,
        )
        assertEquals(1558, result.bmr)
        assertEquals(2687, result.maintenanceCalories)
        assertEquals(300, result.goalAdjustment)
        assertEquals(2987, result.targetCalories)
    }

    @Test
    fun extremeDeficitIsClampedAtSafeMinimum() {
        val result = TdeeCalculator.calculate(
            sex = BiologicalSex.FEMALE,
            weightKg = 42.0,
            heightCm = 150.0,
            ageYears = 45,
            activity = ActivityLevel.SEDENTARY,
            goal = GoalDirection.LOSE,
        )
        assertEquals(1200, result.targetCalories)
    }
}
