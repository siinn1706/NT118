package vn.uit.healthos.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Verifies WCAG AA contrast ratios (4.5:1 normal text) on key light and dark theme token pairs. */
class ThemeContrastTest {

    @Test
    fun lightThemePairsMeetWcagAa() {
        assertAa("light primary/onPrimary", LightColors.primary, LightColors.onPrimary)
        assertAa("light primaryContainer/onPrimaryContainer", LightColors.primaryContainer, LightColors.onPrimaryContainer)
        assertAa("light background/onBackground", LightColors.background, LightColors.onBackground)
        assertAa("light surface/onSurface", LightColors.surface, LightColors.onSurface)
        assertAa("light cardSurface/onSurface", LightVitalColors.cardSurface, LightColors.onSurface)
        assertAa("light surface/onSurfaceVariant", LightColors.surface, LightColors.onSurfaceVariant)
        assertAa("light error/onError", LightColors.error, LightColors.onError)
        assertAa("light errorContainer/onErrorContainer", LightColors.errorContainer, LightColors.onErrorContainer)
        assertAa("light tertiary/onTertiary", LightColors.tertiary, LightColors.onTertiary)
    }

    @Test
    fun darkThemePairsMeetWcagAa() {
        assertAa("dark primary/onPrimary", DarkColors.primary, DarkColors.onPrimary)
        assertAa("dark primaryContainer/onPrimaryContainer", DarkColors.primaryContainer, DarkColors.onPrimaryContainer)
        assertAa("dark background/onBackground", DarkColors.background, DarkColors.onBackground)
        assertAa("dark surface/onSurface", DarkColors.surface, DarkColors.onSurface)
        assertAa("dark cardSurface/onSurface", DarkVitalColors.cardSurface, DarkColors.onSurface)
        assertAa("dark surface/onSurfaceVariant", DarkColors.surface, DarkColors.onSurfaceVariant)
        assertAa("dark error/onError", DarkColors.error, DarkColors.onError)
        assertAa("dark errorContainer/onErrorContainer", DarkColors.errorContainer, DarkColors.onErrorContainer)
        assertAa("dark tertiary/onTertiary", DarkColors.tertiary, DarkColors.onTertiary)
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
