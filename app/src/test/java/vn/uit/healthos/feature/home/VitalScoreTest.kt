package vn.uit.healthos.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VitalScoreTest {

    @Test
    fun perfectDayReturnsOne() {
        val score = VitalScore.compute(
            steps = 10_000,
            stepGoal = 8_000,
            caloriesConsumed = 2_050.0,
            calorieTarget = 2_000,
            hydrationMl = 2_500,
            hydrationTargetMl = 2_250,
            restingHeartRateBpm = 68.0,
        )
        assertEquals(1.0f, score!!, 0.001f)
    }

    @Test
    fun emptyMetricsReturnsNoScore() {
        val score = VitalScore.compute(
            steps = null,
            stepGoal = 8_000,
            caloriesConsumed = null,
            calorieTarget = null,
            hydrationMl = null,
            hydrationTargetMl = 2_000,
            restingHeartRateBpm = null,
        )
        assertNull(score)
    }

    @Test
    fun extremeValuesStayClampedBetweenZeroAndOne() {
        val low = VitalScore.compute(
            steps = 0,
            stepGoal = 8_000,
            caloriesConsumed = 5_000.0,
            calorieTarget = 2_000,
            hydrationMl = 0,
            hydrationTargetMl = 2_500,
            restingHeartRateBpm = 160.0,
        )
        assertEquals(0.0f, low!!, 0.001f)
    }
}
