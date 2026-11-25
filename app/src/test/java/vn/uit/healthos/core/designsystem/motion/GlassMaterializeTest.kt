package vn.uit.healthos.core.designsystem.motion

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.uit.healthos.core.designsystem.glass.MaterializeDenseFrom
import vn.uit.healthos.core.designsystem.glass.glassDensity

class GlassMaterializeTest {

    @Test
    fun `glass stays dense until the last quarter and is gone at zero`() {
        assertEquals(0.25f, MaterializeDenseFrom, 0f)
        assertEquals(1f, glassDensity(1f), 0f)
        assertEquals(1f, glassDensity(0.5f), 0f)
        assertEquals(1f, glassDensity(0.25f), 0f)
        assertEquals(0f, glassDensity(0f), 0f)
        assertEquals(0f, glassDensity(-0.1f), 0f)
        assertEquals(1f, glassDensity(1.05f), 0f)
        assertEquals(0.5f, glassDensity(0.125f), 0.0001f)
    }

    @Test
    fun `dissolving is monotonic and smooth`() {
        var previous = 0f
        var p = 0f
        while (p <= 0.25f) {
            val d = glassDensity(p)
            assertTrue(d >= previous)
            assertTrue(d - previous < 0.02f)
            previous = d
            p += 0.0025f
        }
    }

    @Test
    fun `capsules only join while their gap is under half the blend radius`() {
        val left = Rect(0f, 0f, 100f, 50f)
        assertEquals(0f, sdfNeckHalfWidth(left, Rect(130f, 0f, 230f, 50f), blendRadius = 56f), 0f)
        assertEquals(0f, sdfNeckHalfWidth(left, Rect(110f, 0f, 210f, 50f), blendRadius = 0f), 0f)
        val near = sdfNeckHalfWidth(left, Rect(110f, 0f, 210f, 50f), blendRadius = 56f)
        val touching = sdfNeckHalfWidth(left, Rect(100f, 0f, 200f, 50f), blendRadius = 56f)
        assertTrue(near > 0f && near < touching)
        assertEquals(25f * 0.9f, touching, 0.001f)
    }

    @Test
    fun `gap between rectangles is the shortest edge distance`() {
        val a = Rect(0f, 0f, 10f, 10f)
        assertEquals(0f, rectGap(a, Rect(5f, 5f, 20f, 20f)), 0f)
        assertEquals(5f, rectGap(a, Rect(15f, 0f, 20f, 10f)), 0f)
        assertEquals(5f, rectGap(a, Rect(13f, 14f, 20f, 20f)), 0.0001f)
    }
}
