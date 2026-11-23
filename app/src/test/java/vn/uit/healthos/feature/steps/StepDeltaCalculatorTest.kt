package vn.uit.healthos.feature.steps

import org.junit.Assert.assertEquals
import org.junit.Test

class StepDeltaCalculatorTest {

    @Test
    fun firstReadingSetsBaselineAtZeroSteps() {
        val result = StepDeltaCalculator.applySensorReading(
            todayDate = "2026-11-23",
            newRawCounter = 12_000L,
            existingToday = null,
            latestOverall = null,
            nowMs = 1_000L,
        )
        assertEquals(0, result.steps)
        assertEquals(12_000L, result.lastSensorValue)
    }

    @Test
    fun incrementsWithinSameDay() {
        val existing = DailyStepEntity("2026-11-23", steps = 250, lastSensorValue = 12_250L, updatedAtMs = 1_000L)
        val result = StepDeltaCalculator.applySensorReading(
            todayDate = "2026-11-23",
            newRawCounter = 12_400L,
            existingToday = existing,
            latestOverall = existing,
            nowMs = 2_000L,
        )
        assertEquals(400, result.steps)
        assertEquals(12_400L, result.lastSensorValue)
    }

    @Test
    fun handlesRebootCounterResetMidDay() {
        val existing = DailyStepEntity("2026-11-23", steps = 1_500, lastSensorValue = 40_000L, updatedAtMs = 1_000L)
        val result = StepDeltaCalculator.applySensorReading(
            todayDate = "2026-11-23",
            newRawCounter = 320L,
            existingToday = existing,
            latestOverall = existing,
            nowMs = 5_000L,
        )
        assertEquals(1_820, result.steps)
        assertEquals(320L, result.lastSensorValue)
    }

    @Test
    fun handlesMidnightRollover() {
        val yesterday = DailyStepEntity("2026-11-22", steps = 8_200, lastSensorValue = 50_000L, updatedAtMs = 1_000L)
        val result = StepDeltaCalculator.applySensorReading(
            todayDate = "2026-11-23",
            newRawCounter = 50_075L,
            existingToday = null,
            latestOverall = yesterday,
            nowMs = 2_000L,
        )
        assertEquals("2026-11-23", result.date)
        assertEquals(75, result.steps)
        assertEquals(50_075L, result.lastSensorValue)
    }
}
