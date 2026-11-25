package vn.uit.healthos.feature.home

import vn.uit.healthos.core.data.session.AppPreferences
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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

/** Coarse reading of a Vital Score, using the same thresholds as the Home orb caption. */
enum class VitalLevel { GOOD, WATCH, ALERT }

fun vitalLevelOf(score: Float): VitalLevel = when {
    score >= 0.7f -> VitalLevel.GOOD
    score >= 0.5f -> VitalLevel.WATCH
    else -> VitalLevel.ALERT
}

/**
 * Today's headline numbers shared by Home and the home-screen widgets, merged from the phone's own counters and the
 * cached dashboard. Local counters win while they are ahead of the server, since they include work not yet synced.
 */
data class TodayMetrics(
    val stepsToday: Int,
    val stepGoal: Int,
    val caloriesIn: Double?,
    val calorieTarget: Int?,
    val hydrationMl: Int,
    val hydrationTargetMl: Int,
    val restingHeartRate: Double?,
    val vitalScore: Float?,
) {
    companion object {
        /**
         * [localStepsToday] and [localHydrationMl] are the on-device totals for [today]; [hydrationTargetMl] is the
         * climate/weight-adjusted water goal. Dashboard running totals only count when fetched on [today].
         */
        fun compute(
            cached: CachedDashboard?,
            today: LocalDate,
            localStepsToday: Int,
            localHydrationMl: Int,
            prefs: AppPreferences,
            hydrationTargetMl: Int,
            zone: ZoneId = ZoneId.systemDefault(),
        ): TodayMetrics {
            val fetchedOn = cached?.let { Instant.ofEpochMilli(it.updatedAtMs).atZone(zone).toLocalDate() }
            val kpis = (cached?.summary?.toKpis() ?: DashboardKpis()).let { all ->
                if (fetchedOn == null) all else all.forDay(fetchedOn = fetchedOn, today = today)
            }
            val stepsToday = maxOf(localStepsToday, kpis.steps ?: 0)
            val stepGoal = kpis.stepTarget ?: prefs.stepGoal
            val calories = kpis.caloriesIn
            val calorieTarget = prefs.dailyCalorieTarget ?: kpis.calorieTarget
            val hydrationMl = maxOf(localHydrationMl, kpis.hydrationMl ?: 0)
            val hr = kpis.heartRateBpm
                ?: cached?.vitals?.lastOrNull { it.heartRate != null }?.heartRate

            val score = VitalScore.compute(
                steps = stepsToday.takeIf { it > 0 || kpis.steps != null },
                stepGoal = stepGoal,
                caloriesConsumed = calories,
                calorieTarget = calorieTarget,
                hydrationMl = hydrationMl.takeIf { it > 0 || kpis.hydrationMl != null },
                hydrationTargetMl = hydrationTargetMl,
                restingHeartRateBpm = hr,
            )
            return TodayMetrics(
                stepsToday = stepsToday,
                stepGoal = stepGoal,
                caloriesIn = calories,
                calorieTarget = calorieTarget,
                hydrationMl = hydrationMl,
                hydrationTargetMl = hydrationTargetMl,
                restingHeartRate = hr,
                vitalScore = score,
            )
        }
    }
}
