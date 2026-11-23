package vn.uit.healthos.core.designsystem.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class VitalColorTest {

    private val colors = LightVitalColors

    @Test
    fun `score endpoints map to status colours`() {
        assertEquals(colors.good, vitalColorFor(1f, colors))
        assertEquals(colors.watch, vitalColorFor(0.5f, colors))
        assertEquals(colors.alert, vitalColorFor(0f, colors))
    }

    @Test
    fun `healthy and poor bands stay on their brand colour`() {
        assertEquals(colors.good, vitalColorFor(0.8f, colors))
        assertEquals(colors.good, vitalColorFor(0.7f, colors))
        assertEquals(colors.alert, vitalColorFor(0.2f, colors))
    }

    @Test
    fun `out of range and NaN scores are clamped`() {
        assertEquals(colors.good, vitalColorFor(3f, colors))
        assertEquals(colors.alert, vitalColorFor(-1f, colors))
        assertEquals(colors.alert, vitalColorFor(Float.NaN, colors))
    }
}
