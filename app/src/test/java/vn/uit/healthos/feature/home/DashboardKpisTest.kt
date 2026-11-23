package vn.uit.healthos.feature.home

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class DashboardKpisTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    /** Shape produced by the backend's `get_dashboard_summary`. */
    private val summaryJson = """
        {
          "user_name": "An",
          "alerts": [],
          "kpis": {
            "caloriesBurned": {"current": 256.0, "target": 2000},
            "sleepScore": {"current": 87.5, "target": 100},
            "heartRate": {"current": 72.0, "target": 100},
            "steps": {"current": 6400.0, "target": 10000}
          },
          "goals": [
            {"id": "goal-water", "key": "water", "current": null, "target": 2000, "unit": "ml"},
            {"id": "goal-steps", "key": "steps", "current": 6400.0, "target": 10000, "unit": "steps"},
            {"id": "goal-calories", "key": "calories", "current": 1450.0, "target": 2000, "unit": "kcal"}
          ],
          "ai_insight": {"text": "CALORIE_NORMAL", "category": "nutrition", "insight_code": "CALORIE_NORMAL"}
        }
    """.trimIndent()

    @Test
    fun mapsBackendKeysToTodayValues() {
        val dto = json.decodeFromString(DashboardSummaryDto.serializer(), summaryJson)

        val kpis = dto.toKpis()

        assertEquals(6400, kpis.steps)
        assertEquals(10000, kpis.stepTarget)
        assertEquals(1450.0, kpis.caloriesIn!!, 0.0)
        assertEquals(2000, kpis.calorieTarget)
        assertEquals(72.0, kpis.heartRateBpm!!, 0.0)
        assertNull(kpis.hydrationMl)
        assertEquals("CALORIE_NORMAL", dto.aiInsight?.insightCode)
    }

    @Test
    fun readsHydrationFromWaterGoalWhenReported() {
        val dto = DashboardSummaryDto(
            goals = listOf(DashboardGoalDto(id = "goal-water", key = "water", current = 750.0, target = 2000.0)),
        )

        assertEquals(750, dto.toKpis().hydrationMl)
    }

    @Test
    fun emptySummaryYieldsNoValues() {
        val kpis = DashboardSummaryDto().toKpis()

        assertEquals(DashboardKpis(), kpis)
    }

    @Test
    fun keepsDayTotalsOnTheDayTheSummaryWasFetched() {
        val kpis = DashboardKpis(steps = 6400, stepTarget = 10000, caloriesIn = 1450.0, hydrationMl = 750)
        val day = LocalDate.of(2026, 11, 23)

        assertEquals(kpis, kpis.forDay(fetchedOn = day, today = day))
    }

    @Test
    fun dropsYesterdaysTotalsAfterMidnightButKeepsTargets() {
        val kpis = DashboardKpis(
            steps = 6400,
            stepTarget = 10000,
            caloriesIn = 1450.0,
            calorieTarget = 2000,
            hydrationMl = 750,
            heartRateBpm = 72.0,
        )

        val rolled = kpis.forDay(fetchedOn = LocalDate.of(2026, 11, 23), today = LocalDate.of(2026, 11, 24))

        assertNull(rolled.steps)
        assertNull(rolled.caloriesIn)
        assertNull(rolled.hydrationMl)
        assertEquals(10000, rolled.stepTarget)
        assertEquals(2000, rolled.calorieTarget)
        assertEquals(72.0, rolled.heartRateBpm!!, 0.0)
    }

    @Test
    fun ignoresBurnedCaloriesWhenNoIntakeGoal() {
        val dto = DashboardSummaryDto(
            kpis = mapOf("caloriesBurned" to KpiValueDto(current = 300.0, target = 2000.0)),
            goals = listOf(DashboardGoalDto(id = "goal-calories", key = "calories", current = null, target = 2000.0)),
        )

        val kpis = dto.toKpis()

        assertNull(kpis.caloriesIn)
        assertEquals(2000, kpis.calorieTarget)
    }
}
