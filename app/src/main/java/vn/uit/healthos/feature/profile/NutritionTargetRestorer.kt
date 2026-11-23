package vn.uit.healthos.feature.profile

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import vn.uit.healthos.core.data.session.AppPreferencesRepository
import vn.uit.healthos.core.network.ApiResult
import vn.uit.healthos.feature.onboarding.ActivityLevel
import vn.uit.healthos.feature.onboarding.BiologicalSex
import vn.uit.healthos.feature.onboarding.GoalDirection
import vn.uit.healthos.feature.onboarding.TdeeCalculator
import java.time.LocalDate
import java.time.Period
import javax.inject.Inject
import javax.inject.Singleton

data class NutritionTargets(
    val dailyCalories: Int,
    val targetWeightKg: Double?,
    val activity: ActivityLevel,
    val goal: GoalDirection,
)

/**
 * Rebuilds the daily calorie target from the server profile when this device has none
 * (onboarding finished on another device, or local data was wiped for a different account).
 * The backend does not store activity level, so the last one chosen on this device is reused, else moderate.
 */
@Singleton
class NutritionTargetRestorer @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val preferences: AppPreferencesRepository,
) {
    private val mutex = Mutex()

    suspend fun restoreIfMissing() = mutex.withLock {
        val prefs = preferences.current()
        if (prefs.dailyCalorieTarget != null) return@withLock
        val profile = (profileRepository.getProfile() as? ApiResult.Success)?.data ?: return@withLock
        val targetWeight = (profileRepository.getHealthGoal() as? ApiResult.Success)?.data?.targetWeightKg
        val activity = ActivityLevel.entries.firstOrNull { it.key == prefs.activityLevel } ?: ActivityLevel.MODERATE
        val targets = estimateNutritionTargets(profile, targetWeight, activity, LocalDate.now()) ?: return@withLock
        preferences.setNutritionTargets(
            dailyCalorieTarget = targets.dailyCalories,
            targetWeightKg = targets.targetWeightKg?.toFloat(),
            activityLevel = targets.activity.key,
            goalDirection = targets.goal.key,
        )
    }
}

/** Returns null when the profile lacks the body metrics the formula needs. */
fun estimateNutritionTargets(
    profile: UserProfileDto,
    targetWeightKg: Double?,
    activity: ActivityLevel,
    today: LocalDate,
): NutritionTargets? {
    val weight = profile.weightKg?.takeIf { it > 0 } ?: return null
    val height = profile.heightCm?.takeIf { it > 0 } ?: return null
    val birthDate = profile.dateOfBirth?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() } ?: return null
    val age = Period.between(birthDate, today).years.takeIf { it > 0 } ?: return null
    val sex = BiologicalSex.entries.firstOrNull { it.apiValue == profile.gender } ?: BiologicalSex.OTHER
    val goal = when {
        targetWeightKg == null -> GoalDirection.MAINTAIN
        targetWeightKg < weight - GOAL_TOLERANCE_KG -> GoalDirection.LOSE
        targetWeightKg > weight + GOAL_TOLERANCE_KG -> GoalDirection.GAIN
        else -> GoalDirection.MAINTAIN
    }
    val result = TdeeCalculator.calculate(sex, weight, height, age, activity, goal)
    return NutritionTargets(result.targetCalories, targetWeightKg, activity, goal)
}

private const val GOAL_TOLERANCE_KG = 1.0
