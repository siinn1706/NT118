package vn.uit.healthos.feature.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetFormatTest {

    @Test
    fun progressIsClampedAndSafeWithoutATarget() {
        assertEquals(0.5f, progressOf(1000.0, 2000.0), 0.0001f)
        assertEquals(1f, progressOf(3000.0, 2000.0), 0.0001f)
        assertEquals(0f, progressOf(-5.0, 2000.0), 0.0001f)
        assertEquals(0f, progressOf(1000.0, 0.0), 0.0001f)
    }

    @Test
    fun countsUseVietnameseThousandsSeparators() {
        assertEquals("12.345", formatCount(12_345))
        assertEquals("950", formatCount(950))
    }

    @Test
    fun dependentWidgetSetsCoverTheWidgetsShowingThatNumber() {
        assertEquals(setOf(WidgetKind.STEPS, WidgetKind.VITAL_SCORE), WidgetKind.STEP_DEPENDENT)
        assertEquals(setOf(WidgetKind.HYDRATION, WidgetKind.VITAL_SCORE), WidgetKind.HYDRATION_DEPENDENT)
    }
}
