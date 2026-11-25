package vn.uit.healthos.feature.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import vn.uit.healthos.core.data.session.AppPreferences
import vn.uit.healthos.feature.home.CachedDashboard
import vn.uit.healthos.feature.home.DashboardGoalDto
import vn.uit.healthos.feature.home.DashboardSummaryDto
import vn.uit.healthos.feature.home.ExtendedVitalPointDto
import vn.uit.healthos.feature.home.TodayMetrics
import vn.uit.healthos.feature.home.VitalLevel
import vn.uit.healthos.feature.home.vitalLevelOf
import java.time.LocalDate
import java.time.ZoneId

class TodayMetricsTest {

    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val today = LocalDate.of(2026, 11, 24)
    private val todayNoonMs = today.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    private val yesterdayNoonMs = today.minusDays(1).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    private fun dashboard(
        updatedAtMs: Long,
        steps: Double? = null,
        stepTarget: Double? = null,
        calories: Double? = null,
        calorieTarget: Double? = null,
        water: Double? = null,
        vitals: List<ExtendedVitalPointDto> = emptyList(),
    ) = CachedDashboard(
        summary = DashboardSummaryDto(
            goals = listOf(
                DashboardGoalDto(id = "s", key = "steps", current = steps, target = stepTarget),
                DashboardGoalDto(id = "c", key = "calories", current = calories, target = calorieTarget),
                DashboardGoalDto(id = "w", key = "water", current = water, target = 2000.0),
            ),
        ),
        aiAdvice = null,
        exercises = emptyList(),
        vitals = vitals,
        updatedAtMs = updatedAtMs,
    )

    private fun compute(
        cached: CachedDashboard?,
        localSteps: Int = 0,
        localWater: Int = 0,
        prefs: AppPreferences = AppPreferences(),
        hydrationTargetMl: Int = 2400,
    ) = TodayMetrics.compute(
        cached = cached,
        today = today,
        localStepsToday = localSteps,
        localHydrationMl = localWater,
        prefs = prefs,
        hydrationTargetMl = hydrationTargetMl,
        zone = zone,
    )

    @Test
    fun noDataGivesEmptyScoreAndPreferenceGoals() {
        val metrics = compute(cached = null)

        assertEquals(0, metrics.stepsToday)
        assertEquals(AppPreferences().stepGoal, metrics.stepGoal)
        assertNull(metrics.caloriesIn)
        assertNull(metrics.calorieTarget)
        assertNull(metrics.vitalScore)
    }

    @Test
    fun localCountersWinWhileAheadOfTheServer() {
        val cached = dashboard(updatedAtMs = todayNoonMs, steps = 3000.0, stepTarget = 10000.0, water = 500.0)

        val metrics = compute(cached, localSteps = 4200, localWater = 750)

        assertEquals(4200, metrics.stepsToday)
        assertEquals(10000, metrics.stepGoal)
        assertEquals(750, metrics.hydrationMl)
    }

    @Test
    fun serverTotalsWinWhenAheadOfThePhone() {
        val cached = dashboard(updatedAtMs = todayNoonMs, steps = 9000.0, water = 1500.0)

        val metrics = compute(cached, localSteps = 4200, localWater = 250)

        assertEquals(9000, metrics.stepsToday)
        assertEquals(1500, metrics.hydrationMl)
    }

    @Test
    fun yesterdaysDashboardKeepsTargetsButDropsItsTotals() {
        val cached = dashboard(
            updatedAtMs = yesterdayNoonMs,
            steps = 9000.0,
            stepTarget = 12000.0,
            calories = 1800.0,
            calorieTarget = 2100.0,
            water = 1500.0,
        )

        val metrics = compute(cached, localSteps = 120)

        assertEquals(120, metrics.stepsToday)
        assertEquals(12000, metrics.stepGoal)
        assertNull(metrics.caloriesIn)
        assertEquals(2100, metrics.calorieTarget)
        assertEquals(0, metrics.hydrationMl)
    }

    @Test
    fun userCalorieTargetOverridesTheServerOne() {
        val cached = dashboard(updatedAtMs = todayNoonMs, calories = 1000.0, calorieTarget = 2100.0)

        val metrics = compute(cached, prefs = AppPreferences(dailyCalorieTarget = 1800))

        assertEquals(1800, metrics.calorieTarget)
        assertEquals(1000.0, metrics.caloriesIn!!, 0.0)
    }

    @Test
    fun heartRateFallsBackToTheLatestVitalReading() {
        val cached = dashboard(
            updatedAtMs = todayNoonMs,
            vitals = listOf(
                ExtendedVitalPointDto(date = "2026-11-22", heartRate = 64.0),
                ExtendedVitalPointDto(date = "2026-11-23", heartRate = 71.0),
                ExtendedVitalPointDto(date = "2026-11-24"),
            ),
        )

        val metrics = compute(cached)

        assertEquals(71.0, metrics.restingHeartRate!!, 0.0)
    }

    @Test
    fun scoreUsesTheSharedHydrationTarget() {
        val full = compute(cached = null, localWater = 2400, hydrationTargetMl = 2400)
        val half = compute(cached = null, localWater = 1200, hydrationTargetMl = 2400)

        assertEquals(2400, full.hydrationTargetMl)
        assertNotNull(full.vitalScore)
        assertEquals(1f, full.vitalScore!!, 0.0001f)
        assertEquals(0.5f, half.vitalScore!!, 0.0001f)
    }

    @Test
    fun levelThresholdsMatchTheHomeOrb() {
        assertEquals(VitalLevel.GOOD, vitalLevelOf(0.7f))
        assertEquals(VitalLevel.WATCH, vitalLevelOf(0.69f))
        assertEquals(VitalLevel.WATCH, vitalLevelOf(0.5f))
        assertEquals(VitalLevel.ALERT, vitalLevelOf(0.49f))
    }
}
