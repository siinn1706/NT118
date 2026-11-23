package vn.uit.healthos.feature.home

import kotlin.math.abs

/**
 * Computes a 0..1 health score for the Vital Orb from weighted daily progress:
 * - Steps progress (weight 0.35): steps / stepGoal, capped at 1.0
 * - Hydration progress (weight 0.25): hydrationMl / hydrationTargetMl, capped at 1.0
 * - Calories adherence (weight 0.25): 1.0 within ±10% of TDEE, tapering smoothly down to 0 at ±60%
 * - Resting heart rate (weight 0.15): 1.0 in 50..85 bpm, tapering down outside that range
 *
 * Missing signals are omitted and the remaining weights re-normalized so partial data still yields a fair score.
 * Returns null when no signal is available, so callers can render an empty state instead of a made-up score.
 */
object VitalScore {

    fun compute(
        steps: Int?,
        stepGoal: Int,
        caloriesConsumed: Double?,
        calorieTarget: Int?,
        hydrationMl: Int?,
        hydrationTargetMl: Int,
        restingHeartRateBpm: Double?,
    ): Float? {
        var weightedSum = 0.0
        var totalWeight = 0.0

        if (steps != null && stepGoal > 0) {
            val s = (steps.toDouble() / stepGoal.toDouble()).coerceIn(0.0, 1.0)
            weightedSum += s * 0.35
            totalWeight += 0.35
        }

        if (hydrationMl != null && hydrationTargetMl > 0) {
            val h = (hydrationMl.toDouble() / hydrationTargetMl.toDouble()).coerceIn(0.0, 1.0)
            weightedSum += h * 0.25
            totalWeight += 0.25
        }

        if (caloriesConsumed != null && caloriesConsumed > 0 && calorieTarget != null && calorieTarget > 0) {
            val relDiff = abs(caloriesConsumed - calorieTarget) / calorieTarget.toDouble()
            val c = when {
                relDiff <= 0.10 -> 1.0
                relDiff >= 0.60 -> 0.0
                else -> 1.0 - (relDiff - 0.10) / 0.50
            }
            weightedSum += c * 0.25
            totalWeight += 0.25
        }

        if (restingHeartRateBpm != null && restingHeartRateBpm > 0) {
            val hr = when {
                restingHeartRateBpm in 50.0..85.0 -> 1.0
                restingHeartRateBpm < 50.0 -> (1.0 - (50.0 - restingHeartRateBpm) / 25.0).coerceIn(0.0, 1.0)
                else -> (1.0 - (restingHeartRateBpm - 85.0) / 45.0).coerceIn(0.0, 1.0)
            }
            weightedSum += hr * 0.15
            totalWeight += 0.15
        }

        if (totalWeight <= 0.0) return null
        return (weightedSum / totalWeight).toFloat().coerceIn(0f, 1f)
    }
}
