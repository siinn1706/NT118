package vn.uit.healthos.feature.steps

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * When a different account signs in, the phone's step rows are dropped, so the sensor's cumulative counter is
 * re-anchored at sign-in and only steps taken afterwards count (and sync) for the new account.
 */
class StepAccountBaselineTest {

    private val day = "2026-11-24"

    @Test
    fun previousAccountsStepsAreNotCarriedOverAfterReset() {
        // Account A walked 8,000 steps today: the counter moved from 10,000 to 18,000.
        val accountA = DailyStepEntity(day, steps = 8_000, lastSensorValue = 18_000L, updatedAtMs = 1_000L)

        // Without a reset, B's first reading would continue A's row.
        val withoutReset = StepDeltaCalculator.applySensorReading(day, 18_050L, accountA, accountA, 2_000L)
        assertEquals(8_050, withoutReset.steps)

        // After the reset there are no rows: B's first reading anchors at the current counter value.
        val baseline = StepDeltaCalculator.applySensorReading(day, 18_050L, null, null, 2_000L)
        assertEquals(0, baseline.steps)
        assertEquals(18_050L, baseline.lastSensorValue)

        // Only the delta since sign-in counts for B.
        val next = StepDeltaCalculator.applySensorReading(day, 18_350L, baseline, baseline, 3_000L)
        assertEquals(300, next.steps)
    }

    @Test
    fun previousAccountsLastDayDoesNotSeedTheNextDay() {
        // A's row from yesterday would otherwise credit today with every step since A's last reading.
        val yesterday = DailyStepEntity("2026-11-23", steps = 6_000, lastSensorValue = 30_000L, updatedAtMs = 1_000L)
        val carried = StepDeltaCalculator.applySensorReading(day, 30_400L, null, yesterday, 2_000L)
        assertEquals(400, carried.steps)

        val afterReset = StepDeltaCalculator.applySensorReading(day, 30_400L, null, null, 2_000L)
        assertEquals(0, afterReset.steps)
    }
}
