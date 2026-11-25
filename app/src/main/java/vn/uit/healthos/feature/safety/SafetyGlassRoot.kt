package vn.uit.healthos.feature.safety

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.uit.healthos.core.data.session.AppPreferences
import vn.uit.healthos.core.data.session.AppPreferencesRepository
import vn.uit.healthos.core.designsystem.glass.GlassRoot
import vn.uit.healthos.core.designsystem.glass.LocalGlassColors
import vn.uit.healthos.core.designsystem.glass.auroraFor
import vn.uit.healthos.core.designsystem.theme.AccentPreset
import vn.uit.healthos.core.designsystem.theme.BackgroundStyle
import vn.uit.healthos.core.designsystem.theme.HealthOsTheme
import vn.uit.healthos.core.designsystem.theme.ThemeMode
import vn.uit.healthos.core.designsystem.theme.shiftHue

/**
 * Theme and glass host for the stand-alone safety activities (SOS countdown, emergency card), which run outside
 * MainActivity: the user's appearance preferences are applied the same way, and [alert] swaps the aurora for a red
 * one so an active SOS reads as urgent at a glance. Until the preferences load, the defaults are used, so the screen
 * never waits on storage.
 */
@Composable
internal fun SafetyGlassRoot(
    preferencesRepository: AppPreferencesRepository,
    alert: Boolean,
    content: @Composable BoxScope.() -> Unit,
) {
    val prefs by preferencesRepository.preferences.collectAsStateWithLifecycle<AppPreferences?>(initialValue = null)
    val loaded = prefs
    HealthOsTheme(
        themeMode = loaded?.themeMode ?: ThemeMode.SYSTEM,
        dynamicColor = loaded?.dynamicColor ?: false,
        accentPreset = loaded?.accentPreset ?: AccentPreset.Default,
        backgroundStyle = loaded?.backgroundStyle ?: BackgroundStyle.Default,
        reducedTransparency = loaded?.reducedTransparency ?: false,
    ) {
        val base = LocalGlassColors.current
        val colors = if (alert) {
            val dark = base.base.luminance() < 0.5f
            val seed = MaterialTheme.colorScheme.error
            base.copy(aurora = alertAurora(seed, loaded?.backgroundStyle ?: BackgroundStyle.Default, dark))
        } else {
            base
        }
        CompositionLocalProvider(LocalGlassColors provides colors) {
            GlassRoot(modifier = Modifier.fillMaxSize(), content = content)
        }
    }
}

/**
 * Red-toned aurora: the error colour and close warm neighbours. A plain background style still gets the soft
 * variant, because the colour itself carries the urgency of the screen.
 */
private fun alertAurora(seed: Color, style: BackgroundStyle, dark: Boolean): List<Color> {
    val seeds = listOf(seed, shiftHue(seed, 18f), shiftHue(seed, -24f), shiftHue(seed, 36f))
    val effective = if (style == BackgroundStyle.SOLID) BackgroundStyle.SOFT else style
    return auroraFor(seeds, effective, dark)
}
