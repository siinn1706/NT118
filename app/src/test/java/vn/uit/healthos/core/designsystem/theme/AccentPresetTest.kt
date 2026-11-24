package vn.uit.healthos.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Every accent preset must keep WCAG AA (4.5:1) wherever the accent carries text. */
class AccentPresetTest {

    @Test
    fun everyPresetMeetsWcagAaInLightAndDark() {
        AccentPreset.entries.forEach { preset ->
            listOf(false, true).forEach { dark ->
                val name = "${preset.name} ${if (dark) "dark" else "light"}"
                val accent = preset.colors(dark)
                val scheme = if (dark) DarkColors.withAccent(preset, dark = true) else LightColors.withAccent(preset, dark = false)
                assertAa("$name primary/onPrimary", accent.primary, accent.onPrimary)
                assertAa("$name primaryContainer/onPrimaryContainer", accent.primaryContainer, accent.onPrimaryContainer)
                // Accent used as text or tint on the app background (links, plain buttons, secondary button labels).
                assertAa("$name primary on background", scheme.background, accent.primary)
            }
        }
    }

    @Test
    fun prominentGlassButtonKeepsLabelContrastOverWorstCaseBackdrop() {
        AccentPreset.entries.forEach { preset ->
            // Light mode: a white backdrop lifts the wash the most; dark mode: a black backdrop darkens it the most.
            val light = preset.light
            val lightFill = light.primary.copy(alpha = ProminentTintAlpha).compositeOver(Color.White)
            assertAa("${preset.name} light prominent", lightFill, light.onPrimary)
            val dark = preset.dark
            val darkFill = dark.primary.copy(alpha = ProminentTintAlpha).compositeOver(Color.Black)
            assertAa("${preset.name} dark prominent", darkFill, dark.onPrimary)
        }
    }

    @Test
    fun withAccentReplacesOnlyAccentRoles() {
        val scheme = LightColors.withAccent(AccentPreset.VIOLET, dark = false)
        assertEquals(AccentPreset.VIOLET.light.primary, scheme.primary)
        assertEquals(AccentPreset.VIOLET.dark.primary, scheme.inversePrimary)
        assertEquals(LightColors.surface, scheme.surface)
        assertEquals(LightColors.error, scheme.error)
    }

    @Test
    fun defaultPresetMatchesTheBuiltInTheme() {
        assertEquals(LightColors.primary, AccentPreset.Default.light.primary)
        assertEquals(DarkColors.primary, AccentPreset.Default.dark.primary)
    }

    @Test
    fun hexIsUpperCaseRrggbb() {
        AccentPreset.entries.forEach { preset ->
            assertTrue(preset.hex, Regex("^#[0-9A-F]{6}$").matches(preset.hex))
        }
        assertEquals("#0D6E67", AccentPreset.TEAL.hex)
    }

    @Test
    fun fromHexRoundTripsEveryPreset() {
        AccentPreset.entries.forEach { preset ->
            assertEquals(preset, AccentPreset.fromHex(preset.hex))
            assertEquals(preset, AccentPreset.fromHex(preset.hex.lowercase().removePrefix("#")))
            assertEquals(preset, AccentPreset.fromHex(preset.dark.primary.toHex()))
        }
    }

    @Test
    fun fromHexPicksNearestPreset() {
        // The web default accent (#1965B3) is a mid blue: closest to Ocean.
        assertEquals(AccentPreset.OCEAN, AccentPreset.fromHex("#1965B3"))
        assertEquals(AccentPreset.ROSE, AccentPreset.fromHex("#E11D48"))
        assertEquals(AccentPreset.GRAPHITE, AccentPreset.fromHex("#444444"))
    }

    @Test
    fun fromHexFallsBackToDefaultOnBadInput() {
        assertEquals(AccentPreset.Default, AccentPreset.fromHex(null))
        assertEquals(AccentPreset.Default, AccentPreset.fromHex(""))
        assertEquals(AccentPreset.Default, AccentPreset.fromHex("#12345"))
        assertEquals(AccentPreset.Default, AccentPreset.fromHex("#GG0000"))
    }

    @Test
    fun fromNameFallsBackToDefault() {
        assertEquals(AccentPreset.AMBER, AccentPreset.fromName("AMBER"))
        assertEquals(AccentPreset.Default, AccentPreset.fromName("PINK"))
        assertEquals(BackgroundStyle.SOFT, BackgroundStyle.fromName("SOFT"))
        assertEquals(BackgroundStyle.Default, BackgroundStyle.fromName(null))
    }

    @Test
    fun shiftHueRotatesAndKeepsAlpha() {
        val red = Color(1f, 0f, 0f, 0.5f)
        val green = shiftHue(red, 120f)
        assertClose(0f, green.red)
        assertClose(1f, green.green)
        assertClose(0f, green.blue)
        assertClose(0.5f, green.alpha)
        val back = shiftHue(green, -120f)
        assertClose(1f, back.red)
        // A grey has no hue and stays put.
        val grey = Color(0.4f, 0.4f, 0.4f)
        assertEquals(grey.toHex(), shiftHue(grey, 90f).toHex())
    }

    private fun assertClose(expected: Float, actual: Float) {
        assertTrue("expected $expected but was $actual", abs(expected - actual) < 0.01f)
    }

    private fun assertAa(label: String, bg: Color, fg: Color) {
        val ratio = contrastRatio(bg, fg)
        assertTrue("$label contrast ratio $ratio is below WCAG AA 4.5:1", ratio >= 4.5)
    }

    private fun contrastRatio(a: Color, b: Color): Double {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    private fun relativeLuminance(c: Color): Double {
        fun linear(channel: Float): Double {
            val v = channel.toDouble()
            return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * linear(c.red) + 0.7152 * linear(c.green) + 0.0722 * linear(c.blue)
    }
}
