package vn.uit.healthos.core.designsystem.glass

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import vn.uit.healthos.core.designsystem.theme.AccentPreset
import vn.uit.healthos.core.designsystem.theme.BackgroundStyle
import vn.uit.healthos.core.designsystem.theme.shiftHue

/*
 * The aurora is four soft light blobs behind every screen. It is derived from the accent so that the glass, the tint
 * and the backdrop always agree: blob 0 is the accent itself, blob 1 is the accent shifted 30 degrees (a neighbour),
 * blobs 2 and 3 are warm counterpoints 150 and 220 degrees away. Light mode uses the bright tone of the accent,
 * dark mode the deep one.
 */
private val LightAuroraAlpha = floatArrayOf(0.55f, 0.45f, 0.38f, 0.42f)
private val DarkAuroraAlpha = floatArrayOf(0.60f, 0.45f, 0.34f, 0.50f)
private const val SoftAuroraFactor = 0.5f

/** Glass colours for an accent preset, background style and appearance. */
fun glassColorsFor(accent: AccentPreset, style: BackgroundStyle, dark: Boolean): GlassColors {
    // The bright accent tone lights a pale base; the deep tone glows on black.
    val seed = if (dark) accent.light.primary else accent.dark.primary
    val seeds = listOf(seed, shiftHue(seed, 30f), shiftHue(seed, 150f), shiftHue(seed, 220f))
    return glassColorsFromSeeds(seeds, style, dark)
}

/** Glass colours that follow a dynamic (Material You) scheme, so the aurora matches the wallpaper palette. */
fun glassColorsFor(scheme: ColorScheme, style: BackgroundStyle, dark: Boolean): GlassColors {
    // inversePrimary is the opposite appearance's primary: pastel in light mode, deep in dark mode.
    val seeds = listOf(scheme.inversePrimary, scheme.primaryContainer, scheme.tertiaryContainer, scheme.secondaryContainer)
    return glassColorsFromSeeds(seeds, style, dark)
}

/** Aurora colours for [seeds] (one per blob) at the strength [style] asks for; empty for [BackgroundStyle.SOLID]. */
fun auroraFor(seeds: List<Color>, style: BackgroundStyle, dark: Boolean): List<Color> {
    if (style == BackgroundStyle.SOLID) return emptyList()
    val alphas = if (dark) DarkAuroraAlpha else LightAuroraAlpha
    val factor = if (style == BackgroundStyle.SOFT) SoftAuroraFactor else 1f
    return seeds.mapIndexed { index, color -> color.copy(alpha = alphas[index % alphas.size] * factor) }
}

private fun glassColorsFromSeeds(seeds: List<Color>, style: BackgroundStyle, dark: Boolean): GlassColors {
    val aurora = auroraFor(seeds, style, dark)
    return if (dark) {
        GlassColors(
            chromeFill = Color(0xFF1C1C1E).copy(alpha = 0.50f),
            materialFill = Color(0xFF1C1C1E).copy(alpha = 0.64f),
            controlFill = Color.White.copy(alpha = 0.10f),
            solidFill = Color(0xFF1C1C1E).copy(alpha = 0.94f),
            rim = Color.White.copy(alpha = 0.12f),
            shadow = Color.Black.copy(alpha = 0.45f),
            base = Color(0xFF000000),
            aurora = aurora,
            insetFill = Color(0xFF767680).copy(alpha = 0.24f),
        )
    } else {
        GlassColors(
            chromeFill = Color.White.copy(alpha = 0.46f),
            materialFill = Color.White.copy(alpha = 0.62f),
            controlFill = Color.White.copy(alpha = 0.30f),
            solidFill = Color.White.copy(alpha = 0.92f),
            rim = Color.Black.copy(alpha = 0.06f),
            shadow = Color.Black.copy(alpha = 0.10f),
            base = Color(0xFFF2F2F7),
            aurora = aurora,
            insetFill = Color(0xFF767680).copy(alpha = 0.12f),
        )
    }
}

val LightGlassColors: GlassColors = glassColorsFor(AccentPreset.Default, BackgroundStyle.Default, dark = false)

val DarkGlassColors: GlassColors = glassColorsFor(AccentPreset.Default, BackgroundStyle.Default, dark = true)
