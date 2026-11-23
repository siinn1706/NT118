package vn.uit.healthos.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/*
 * Role mapping: primary = brand teal; secondary = sage (Material uses it for selection states such as chips and
 * the nav indicator); tertiary = coral accent (hearts, SOS, highlight CTAs). Amber lives in [VitalColors.energy].
 */
internal val LightColors = lightColorScheme(
    primary = Color(0xFF0F766E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCCFBF1),
    onPrimaryContainer = Color(0xFF053B36),
    inversePrimary = Color(0xFF5EEAD4),
    secondary = Color(0xFF4A635F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCE8E2),
    onSecondaryContainer = Color(0xFF05201C),
    tertiary = Color(0xFFE11D48),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFE4E8),
    onTertiaryContainer = Color(0xFF5C0A1F),
    background = Color(0xFFF4FAF7),
    onBackground = Color(0xFF13201C),
    surface = Color(0xFFF4FAF7),
    onSurface = Color(0xFF13201C),
    surfaceVariant = Color(0xFFE6EFEA),
    onSurfaceVariant = Color(0xFF5B6B66),
    surfaceTint = Color(0xFF0F766E),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF9FCFA),
    surfaceContainer = Color(0xFFEEF5F1),
    surfaceContainerHigh = Color(0xFFE6EFEA),
    surfaceContainerHighest = Color(0xFFDEE8E3),
    inverseSurface = Color(0xFF13201C),
    inverseOnSurface = Color(0xFFE6F0EC),
    outline = Color(0xFF7A8C86),
    outlineVariant = Color(0xFFD3DFDA),
    error = Color(0xFFDC2626),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
    scrim = Color(0xFF000000),
)

internal val DarkColors = darkColorScheme(
    primary = Color(0xFF5EEAD4),
    onPrimary = Color(0xFF04201C),
    primaryContainer = Color(0xFF115E56),
    onPrimaryContainer = Color(0xFFCCFBF1),
    inversePrimary = Color(0xFF0F766E),
    secondary = Color(0xFFB1CCC6),
    onSecondary = Color(0xFF1C3531),
    secondaryContainer = Color(0xFF334B47),
    onSecondaryContainer = Color(0xFFCCE8E2),
    tertiary = Color(0xFFFDA4AF),
    onTertiary = Color(0xFF4C0519),
    tertiaryContainer = Color(0xFF881337),
    onTertiaryContainer = Color(0xFFFFE4E8),
    background = Color(0xFF0A1512),
    onBackground = Color(0xFFE6F0EC),
    surface = Color(0xFF0A1512),
    onSurface = Color(0xFFE6F0EC),
    surfaceVariant = Color(0xFF1B2A25),
    onSurfaceVariant = Color(0xFF93A7A0),
    surfaceTint = Color(0xFF5EEAD4),
    surfaceContainerLowest = Color(0xFF07100E),
    surfaceContainerLow = Color(0xFF101B18),
    surfaceContainer = Color(0xFF14211D),
    surfaceContainerHigh = Color(0xFF1B2A25),
    surfaceContainerHighest = Color(0xFF22332D),
    inverseSurface = Color(0xFFE6F0EC),
    inverseOnSurface = Color(0xFF13201C),
    outline = Color(0xFF5F736C),
    outlineVariant = Color(0xFF2A3A35),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2),
    scrim = Color(0xFF000000),
)

/**
 * Status colours for the Vital Orb and health states: good = teal, watch = amber, alert = coral.
 * [energy] is the text/icon-safe amber for calories and streaks.
 */
@Immutable
data class VitalColors(
    val good: Color,
    val watch: Color,
    val alert: Color,
    val energy: Color,
    val cardSurface: Color,
)

internal val LightVitalColors = VitalColors(
    good = Color(0xFF14B8A6),
    watch = Color(0xFFF59E0B),
    alert = Color(0xFFF43F5E),
    energy = Color(0xFFB45309),
    cardSurface = Color(0xFFFFFFFF),
)

internal val DarkVitalColors = VitalColors(
    good = Color(0xFF2DD4BF),
    watch = Color(0xFFFBBF24),
    alert = Color(0xFFFB7185),
    energy = Color(0xFFFCD34D),
    cardSurface = Color(0xFF14211D),
)

val LocalVitalColors = staticCompositionLocalOf { LightVitalColors }

/**
 * Maps a 0..1 health score to a colour: >= 0.7 good, 0.5 watch, <= 0.3 alert.
 * Transitions are kept narrow because blending teal with amber passes through an off-brand green.
 */
fun vitalColorFor(score: Float, colors: VitalColors): Color {
    val s = if (score.isNaN()) 0f else score.coerceIn(0f, 1f)
    return when {
        s >= 0.7f -> colors.good
        s >= 0.5f -> lerpColor(colors.watch, colors.good, (s - 0.5f) / 0.2f)
        s > 0.3f -> lerpColor(colors.alert, colors.watch, (s - 0.3f) / 0.2f)
        else -> colors.alert
    }
}

private fun lerpColor(start: Color, stop: Color, fraction: Float): Color = Color(
    red = start.red + (stop.red - start.red) * fraction,
    green = start.green + (stop.green - start.green) * fraction,
    blue = start.blue + (stop.blue - start.blue) * fraction,
    alpha = start.alpha + (stop.alpha - start.alpha) * fraction,
)
