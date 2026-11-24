package vn.uit.healthos.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/*
 * Role mapping: primary = brand teal (the app tint, like an iOS accent colour); secondary = sage (Material uses it
 * for selection states such as chips); tertiary = coral accent (hearts, SOS, highlight CTAs). Amber lives in
 * [VitalColors.energy]. Neutrals follow Apple's grouped system palette so Material widgets sit naturally on the
 * Liquid Glass surfaces: light mode is built on systemGroupedBackground, dark mode on true black.
 */
internal val LightColors = lightColorScheme(
    primary = Color(0xFF0F766E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3F5EE),
    onPrimaryContainer = Color(0xFF053B36),
    inversePrimary = Color(0xFF5EEAD4),
    secondary = Color(0xFF4A635F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD5EBE6),
    onSecondaryContainer = Color(0xFF05201C),
    tertiary = Color(0xFFE11D48),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFE4E8),
    onTertiaryContainer = Color(0xFF5C0A1F),
    background = Color(0xFFF2F2F7),
    onBackground = Color(0xFF1C1C1E),
    surface = Color(0xFFF2F2F7),
    onSurface = Color(0xFF1C1C1E),
    surfaceVariant = Color(0xFFE5E5EA),
    onSurfaceVariant = Color(0xFF636366),
    surfaceTint = Color(0xFF0F766E),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF7F7FA),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    surfaceContainerHighest = Color(0xFFE5E5EA),
    inverseSurface = Color(0xFF1C1C1E),
    inverseOnSurface = Color(0xFFF2F2F7),
    outline = Color(0xFF8E8E93),
    outlineVariant = Color(0xFFD1D1D6),
    error = Color(0xFFD70015),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFE5E7),
    onErrorContainer = Color(0xFF7A0010),
    scrim = Color(0xFF000000),
)

internal val DarkColors = darkColorScheme(
    primary = Color(0xFF5EEAD4),
    onPrimary = Color(0xFF04201C),
    primaryContainer = Color(0xFF0E4F48),
    onPrimaryContainer = Color(0xFFCCFBF1),
    inversePrimary = Color(0xFF0F766E),
    secondary = Color(0xFFB1CCC6),
    onSecondary = Color(0xFF1C3531),
    secondaryContainer = Color(0xFF2C3E3A),
    onSecondaryContainer = Color(0xFFCCE8E2),
    tertiary = Color(0xFFFDA4AF),
    onTertiary = Color(0xFF4C0519),
    tertiaryContainer = Color(0xFF881337),
    onTertiaryContainer = Color(0xFFFFE4E8),
    background = Color(0xFF000000),
    onBackground = Color(0xFFF5F5F7),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFF5F5F7),
    surfaceVariant = Color(0xFF1C1C1E),
    onSurfaceVariant = Color(0xFF98989F),
    surfaceTint = Color(0xFF5EEAD4),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF111113),
    surfaceContainer = Color(0xFF1C1C1E),
    surfaceContainerHigh = Color(0xFF2C2C2E),
    surfaceContainerHighest = Color(0xFF3A3A3C),
    inverseSurface = Color(0xFFF5F5F7),
    inverseOnSurface = Color(0xFF1C1C1E),
    outline = Color(0xFF8E8E93),
    outlineVariant = Color(0xFF38383A),
    error = Color(0xFFFF6961),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF6E0F12),
    onErrorContainer = Color(0xFFFFE5E7),
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
    cardSurface = Color(0xFF1C1C1E),
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
