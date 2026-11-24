package vn.uit.healthos.core.designsystem.glass

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.uit.healthos.core.designsystem.theme.AccentPreset
import vn.uit.healthos.core.designsystem.theme.BackgroundStyle
import kotlin.math.abs

class GlassMathTest {

    @Test
    fun concentricRadiusSubtractsInsetWithFloor() {
        assertEquals(10.dp, concentricRadius(26.dp, 16.dp))
        assertEquals(4.dp, concentricRadius(26.dp, 30.dp))
        assertEquals(8.dp, concentricRadius(12.dp, 10.dp, min = 8.dp))
    }

    @Test
    fun angleDistanceWrapsAround() {
        assertEquals(20f, angleDistance(170f, -170f), 0.001f)
        assertEquals(90f, angleDistance(0f, 270f), 0.001f)
        assertEquals(0f, angleDistance(45f, 405f), 0.001f)
    }

    @Test
    fun gravityFilterReturnsNullWhileFlat() {
        val filter = GravityFilter()
        assertNull(filter.update(0.1f, 0.2f))
    }

    @Test
    fun gravityFilterFollowsTiltSmoothly() {
        val filter = GravityFilter(smoothing = 0.5f)
        // Upright phone: gravity along +y.
        val upright = filter.update(0f, 9.8f)
        assertNotNull(upright)
        assertEquals(90f, upright!!, 0.01f)
        // A sudden turn onto the side is only followed half way on the next sample (low-pass).
        val next = filter.update(9.8f, 0f)!!
        assertEquals(45f, next, 0.01f)
        filter.reset()
        assertEquals(0f, filter.update(9.8f, 0f)!!, 0.01f)
    }

    @Test
    fun gravityFilterDoesNotJumpAcrossTheSeam() {
        val filter = GravityFilter(smoothing = 0.5f)
        filter.update(-9.8f, 0.5f)
        val angle = filter.update(-9.8f, -0.5f)!!
        // Averaging vectors (not angles) keeps the result next to 180 degrees instead of swinging through 0.
        assertTrue("angle $angle", abs(abs(angle) - 180f) < 5f)
    }

    @Test
    fun solidBackgroundHasNoAurora() {
        assertTrue(glassColorsFor(AccentPreset.OCEAN, BackgroundStyle.SOLID, dark = false).aurora.isEmpty())
    }

    @Test
    fun softAuroraIsHalfAsStrong() {
        val seeds = listOf(Color.Red, Color.Green, Color.Blue, Color.Yellow)
        val full = auroraFor(seeds, BackgroundStyle.AURORA, dark = true)
        val soft = auroraFor(seeds, BackgroundStyle.SOFT, dark = true)
        assertEquals(4, full.size)
        // Colours store alpha in 8 bits, hence the tolerance.
        full.zip(soft).forEach { (f, s) -> assertEquals(f.alpha / 2f, s.alpha, 0.01f) }
    }

    @Test
    fun auroraFollowsTheAccent() {
        val teal = glassColorsFor(AccentPreset.TEAL, BackgroundStyle.AURORA, dark = false).aurora
        val rose = glassColorsFor(AccentPreset.ROSE, BackgroundStyle.AURORA, dark = false).aurora
        assertEquals(4, teal.size)
        assertTrue(teal.first() != rose.first())
        // The first blob is the accent itself (its bright tone in light mode).
        val seed = AccentPreset.ROSE.dark.primary
        assertEquals(seed.red, rose.first().red, 0.001f)
        assertEquals(seed.green, rose.first().green, 0.001f)
    }

    @Test
    fun darkAppearanceUsesBlackBase() {
        assertEquals(Color(0xFF000000), glassColorsFor(AccentPreset.Default, BackgroundStyle.AURORA, dark = true).base)
        assertEquals(Color(0xFFF2F2F7), LightGlassColors.base)
    }

    @Test
    fun stylesFollowHigLayering() {
        assertTrue(GlassStyle.Chrome.hasLens && GlassStyle.Chrome.depthEffect)
        assertTrue(GlassStyle.Control.hasLens && GlassStyle.Control.depthEffect)
        assertTrue(!GlassStyle.Material.hasLens)
        assertEquals(8.dp, GlassStyle.Chrome.blur)
    }
}
