package vn.uit.healthos.feature.onboarding

import kotlin.math.pow
import kotlin.math.roundToInt

enum class BiologicalSex(val apiValue: String) {
    MALE("male"),
    FEMALE("female"),
    OTHER("other"),
}

enum class ActivityLevel(val factor: Double, val key: String) {
    SEDENTARY(1.2, "sedentary"),
    LIGHT(1.375, "light"),
    MODERATE(1.55, "moderate"),
    ACTIVE(1.725, "active"),
    VERY_ACTIVE(1.9, "very_active"),
}

enum class GoalDirection(val calorieAdjustment: Int, val key: String) {
    LOSE(-500, "lose"),
    MAINTAIN(0, "maintain"),
    GAIN(300, "gain"),
}

data class TdeeResult(
    val bmr: Int,
    val maintenanceCalories: Int,
    val goalAdjustment: Int,
    val targetCalories: Int,
    val activityFactor: Double,
    val bmi: Double,
)

/**
 * Mifflin–St Jeor BMR × activity multiplier + goal calorie adjustment.
 * Male:   10*weight(kg) + 6.25*height(cm) - 5*age + 5
 * Female: 10*weight(kg) + 6.25*height(cm) - 5*age - 161
 * Other:  midpoint constant (-78)
 */
object TdeeCalculator {

    private const val MIN_SAFE_CALORIES = 1200

    fun calculate(
        sex: BiologicalSex,
        weightKg: Double,
        heightCm: Double,
        ageYears: Int,
        activity: ActivityLevel,
        goal: GoalDirection,
    ): TdeeResult {
        require(weightKg > 0.0 && heightCm > 0.0 && ageYears > 0) {
            "Body metrics must be positive"
        }
        val sexOffset = when (sex) {
            BiologicalSex.MALE -> 5.0
            BiologicalSex.FEMALE -> -161.0
            BiologicalSex.OTHER -> -78.0
        }
        val bmrRaw = 10.0 * weightKg + 6.25 * heightCm - 5.0 * ageYears + sexOffset
        val bmr = bmrRaw.roundToInt()
        val maintenance = (bmrRaw * activity.factor).roundToInt()
        val target = (maintenance + goal.calorieAdjustment).coerceAtLeast(MIN_SAFE_CALORIES)
        val heightM = heightCm / 100.0
        val bmi = ((weightKg / heightM.pow(2.0)) * 10.0).roundToInt() / 10.0
        return TdeeResult(
            bmr = bmr,
            maintenanceCalories = maintenance,
            goalAdjustment = goal.calorieAdjustment,
            targetCalories = target,
            activityFactor = activity.factor,
            bmi = bmi,
        )
    }
}
