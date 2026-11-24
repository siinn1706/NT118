package vn.uit.healthos.core.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Test

class SliderStepsTest {

    @Test
    fun noStepsOnlyClamps() {
        assertEquals(0.37f, snapToSteps(0.37f, 0f..1f, 0), 0.0001f)
        assertEquals(1f, snapToSteps(1.4f, 0f..1f, 0), 0.0001f)
        assertEquals(0f, snapToSteps(-2f, 0f..1f, 0), 0.0001f)
    }

    @Test
    fun snapsToNearestStop() {
        // 1..5 with 3 steps has stops 1, 2, 3, 4, 5.
        assertEquals(3f, snapToSteps(3.4f, 1f..5f, 3), 0.0001f)
        assertEquals(4f, snapToSteps(3.6f, 1f..5f, 3), 0.0001f)
        assertEquals(5f, snapToSteps(9f, 1f..5f, 3), 0.0001f)
        assertEquals(0.5f, snapToSteps(0.55f, 0f..1f, 1), 0.0001f)
    }

    @Test
    fun emptyRangeReturnsStart() {
        assertEquals(2f, snapToSteps(2f, 2f..2f, 4), 0.0001f)
    }
}
