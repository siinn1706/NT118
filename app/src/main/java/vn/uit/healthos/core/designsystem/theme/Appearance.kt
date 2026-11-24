package vn.uit.healthos.core.designsystem.theme

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import vn.uit.healthos.R
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** The four accent roles a preset replaces in the Material colour scheme. */
@Immutable
data class AccentColors(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
)

/**
 * User-selectable accent colour (the iOS "tint"). Every preset is hand-tuned for light and dark so that
 * primary/onPrimary, primaryContainer/onPrimaryContainer, primary text on the app background and the prominent
 * glass button ([ProminentTintAlpha] over a worst-case backdrop) all reach WCAG AA (4.5:1).
 *
 * [hex] is the value synced to the backend `accent_color` field (#RRGGBB, upper case).
 */
enum class AccentPreset(
    @param:StringRes val label: Int,
    val light: AccentColors,
    val dark: AccentColors,
) {
    TEAL(
        R.string.accent_teal,
        light = AccentColors(Color(0xFF0D6E67), Color.White, Color(0xFFD3F5EE), Color(0xFF053B36)),
        dark = AccentColors(Color(0xFF5EEAD4), Color(0xFF04201C), Color(0xFF0E4F48), Color(0xFFCCFBF1)),
    ),
    OCEAN(
        R.string.accent_ocean,
        light = AccentColors(Color(0xFF0A5CAE), Color.White, Color(0xFFD8E8FB), Color(0xFF032A52)),
        dark = AccentColors(Color(0xFF82B8FF), Color(0xFF04213F), Color(0xFF0F3D6E), Color(0xFFD8E8FB)),
    ),
    VIOLET(
        R.string.accent_violet,
        light = AccentColors(Color(0xFF6D28D9), Color.White, Color(0xFFEDE7FE), Color(0xFF2E1065)),
        dark = AccentColors(Color(0xFFC4B5FD), Color(0xFF24104F), Color(0xFF4C2A99), Color(0xFFEDE7FE)),
    ),
    ROSE(
        R.string.accent_rose,
        light = AccentColors(Color(0xFFBE123C), Color.White, Color(0xFFFFE4E8), Color(0xFF5C0A1F)),
        dark = AccentColors(Color(0xFFFDA4AF), Color(0xFF4C0519), Color(0xFF881337), Color(0xFFFFE4E8)),
    ),
    AMBER(
        R.string.accent_amber,
        light = AccentColors(Color(0xFF9A4700), Color.White, Color(0xFFFDEBD0), Color(0xFF4A2300)),
        dark = AccentColors(Color(0xFFFCC05A), Color(0xFF3A1D00), Color(0xFF6B3A00), Color(0xFFFDEBD0)),
    ),
    FOREST(
        R.string.accent_forest,
        light = AccentColors(Color(0xFF2A7130), Color.White, Color(0xFFDDF2DE), Color(0xFF0F3311)),
        dark = AccentColors(Color(0xFF8BDA8F), Color(0xFF0B2E0D), Color(0xFF1F5A23), Color(0xFFDDF2DE)),
    ),
    GRAPHITE(
        R.string.accent_graphite,
        light = AccentColors(Color(0xFF3A3A3C), Color.White, Color(0xFFE5E5EA), Color(0xFF1C1C1E)),
        dark = AccentColors(Color(0xFFD1D1D6), Color(0xFF1C1C1E), Color(0xFF3A3A3C), Color(0xFFF2F2F7)),
    ),
    ;

    fun colors(dark: Boolean): AccentColors = if (dark) this.dark else light

    /** Server representation (#RRGGBB, upper case) of the light-mode accent. */
    val hex: String get() = light.primary.toHex()

    companion object {
        val Default = TEAL

        /**
         * Maps a server `accent_color` (#RRGGBB, any case, with or without '#') to the closest preset by RGB
         * distance against both the light and dark variants. Returns [Default] for blank or malformed input.
         */
        fun fromHex(hex: String?): AccentPreset {
            val color = parseHex(hex) ?: return Default
            return entries.minBy { preset ->
                min(distance(color, preset.light.primary), distance(color, preset.dark.primary))
            }
        }

        /** Stored enum name back to a preset; unknown names fall back to [Default]. */
        fun fromName(name: String?): AccentPreset =
            entries.firstOrNull { it.name == name } ?: Default
    }
}

/** How much of the ambient aurora is painted behind the glass. */
enum class BackgroundStyle(@param:StringRes val label: Int) {
    /** Full-strength accent aurora: the most "liquid" look. */
    AURORA(R.string.background_aurora),

    /** Aurora at half strength: calmer, still gives glass something to refract. */
    SOFT(R.string.background_soft),

    /** Only the flat base colour: easiest to read and cheapest to draw. */
    SOLID(R.string.background_solid),
    ;

    companion object {
        val Default = AURORA

        fun fromName(name: String?): BackgroundStyle =
            entries.firstOrNull { it.name == name } ?: Default
    }
}

/**
 * Alpha of the accent wash drawn on top of the hue-blended backdrop by prominent glass buttons. Chosen so that
 * onPrimary on (accent at this alpha over a white backdrop in light mode, black in dark mode) stays >= 4.5:1 for
 * every [AccentPreset]; ThemeContrastTest guards it.
 */
const val ProminentTintAlpha = 0.88f

internal fun Color.toHex(): String {
    fun channel(v: Float) = (v * 255f + 0.5f).toInt().coerceIn(0, 255)
    return String.format(Locale.ROOT, "#%02X%02X%02X", channel(red), channel(green), channel(blue))
}

internal fun parseHex(hex: String?): Color? {
    val digits = hex?.trim()?.removePrefix("#") ?: return null
    if (digits.length != 6 || digits.any { it.digitToIntOrNull(16) == null }) return null
    val rgb = digits.toLong(16)
    return Color(0xFF000000 or rgb)
}

private fun distance(a: Color, b: Color): Float {
    val dr = a.red - b.red
    val dg = a.green - b.green
    val db = a.blue - b.blue
    // Weighted towards green like human luminance sensitivity, so near-greys do not snap to a saturated hue.
    return 0.3f * dr * dr + 0.59f * dg * dg + 0.11f * db * db
}

/** Rotates the hue of [color] by [degrees] in HSV space, keeping saturation, value and alpha. */
fun shiftHue(color: Color, degrees: Float): Color {
    val r = color.red
    val g = color.green
    val b = color.blue
    val maxC = max(r, max(g, b))
    val minC = min(r, min(g, b))
    val delta = maxC - minC
    val hue = when {
        delta == 0f -> 0f
        maxC == r -> 60f * (((g - b) / delta) % 6f)
        maxC == g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }
    val saturation = if (maxC == 0f) 0f else delta / maxC
    val shifted = ((hue + degrees) % 360f + 360f) % 360f
    return hsv(shifted, saturation, maxC, color.alpha)
}

private fun hsv(hue: Float, saturation: Float, value: Float, alpha: Float): Color {
    val c = value * saturation
    val x = c * (1f - abs((hue / 60f) % 2f - 1f))
    val m = value - c
    val (r, g, b) = when {
        hue < 60f -> Triple(c, x, 0f)
        hue < 120f -> Triple(x, c, 0f)
        hue < 180f -> Triple(0f, c, x)
        hue < 240f -> Triple(0f, x, c)
        hue < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(red = r + m, green = g + m, blue = b + m, alpha = alpha)
}
