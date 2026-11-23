package vn.uit.healthos.feature.home

import java.time.LocalDate

/**
 * Today's values extracted from `GET /v1/dashboard/summary`.
 *
 * The backend keys activity KPIs as `steps`, `heartRate`, `caloriesBurned`, `sleepScore`,
 * while intake-style values (calories eaten, water) live in `goals[]` keyed `calories` / `water`.
 * A null field means the server reported no value.
 */
data class DashboardKpis(
    val steps: Int? = null,
    val stepTarget: Int? = null,
    val caloriesIn: Double? = null,
    val calorieTarget: Int? = null,
    val hydrationMl: Int? = null,
    val heartRateBpm: Double? = null,
)

object DashboardKeys {
    const val KPI_STEPS = "steps"
    const val KPI_HEART_RATE = "heartRate"
    const val GOAL_STEPS = "steps"
    const val GOAL_CALORIES = "calories"
    const val GOAL_WATER = "water"
}

/**
 * The summary only describes the local day it was fetched on. On a later day its running totals
 * (steps, calories eaten, water) are dropped; targets and the latest heart rate still apply.
 */
fun DashboardKpis.forDay(fetchedOn: LocalDate, today: LocalDate): DashboardKpis =
    if (fetchedOn == today) this else copy(steps = null, caloriesIn = null, hydrationMl = null)

fun DashboardSummaryDto.toKpis(): DashboardKpis {
    fun goal(key: String): DashboardGoalDto? = goals.firstOrNull { it.key == key }
    val stepsKpi = kpis[DashboardKeys.KPI_STEPS]
    val stepsGoal = goal(DashboardKeys.GOAL_STEPS)
    val caloriesGoal = goal(DashboardKeys.GOAL_CALORIES)
    val waterGoal = goal(DashboardKeys.GOAL_WATER)

    return DashboardKpis(
        steps = (stepsKpi?.current ?: stepsGoal?.current)?.toInt(),
        stepTarget = (stepsKpi?.target ?: stepsGoal?.target)?.toInt()?.takeIf { it > 0 },
        caloriesIn = caloriesGoal?.current,
        calorieTarget = caloriesGoal?.target?.toInt()?.takeIf { it > 0 },
        hydrationMl = waterGoal?.current?.toInt(),
        heartRateBpm = kpis[DashboardKeys.KPI_HEART_RATE]?.current,
    )
}
