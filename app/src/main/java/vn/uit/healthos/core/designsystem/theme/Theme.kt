package vn.uit.healthos.core.designsystem.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import vn.uit.healthos.core.designsystem.glass.LocalGlassColors
import vn.uit.healthos.core.designsystem.glass.glassColorsFor

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * HealthOS theme: Material 3 colours tuned for Liquid Glass, the accent [accentPreset] (ignored while [dynamicColor]
 * is on, where the wallpaper palette wins), the ambient [backgroundStyle], and the accessibility locals
 * [LocalReducedMotion] (system "Remove animations", observed live) and [LocalReducedTransparency] ([reducedTransparency]
 * from settings, or the system high-contrast text setting), which routes every glass surface to its opaque path.
 */
@Composable
fun HealthOsTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    accentPreset: AccentPreset = AccentPreset.Default,
    backgroundStyle: BackgroundStyle = BackgroundStyle.Default,
    reducedTransparency: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val useDynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val context = LocalContext.current
    val colorScheme = remember(useDynamic, dark, accentPreset, context) {
        when {
            useDynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            dark -> DarkColors.withAccent(accentPreset, dark = true)
            else -> LightColors.withAccent(accentPreset, dark = false)
        }
    }
    val glassColors = remember(colorScheme, useDynamic, accentPreset, backgroundStyle, dark) {
        if (useDynamic) {
            glassColorsFor(colorScheme, backgroundStyle, dark)
        } else {
            glassColorsFor(accentPreset, backgroundStyle, dark)
        }
    }
    val systemReducedMotion = observeSystemReducedMotion()
    SystemBarsAppearance(dark = dark)
    val systemHighContrast = observeSystemHighTextContrast()

    CompositionLocalProvider(
        LocalVitalColors provides if (dark) DarkVitalColors else LightVitalColors,
        LocalGlassColors provides glassColors,
        LocalReducedMotion provides systemReducedMotion.value,
        LocalReducedTransparency provides (reducedTransparency || systemHighContrast.value),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = HealthOsTypography,
            shapes = HealthOsShapes,
            content = content,
        )
    }
}

/**
 * Keeps the status and navigation bar icons legible on the app's own theme: the window follows the system appearance by
 * default, so with the app set to dark on a light system the dark icons would vanish on the dark backdrop.
 */
@Composable
private fun SystemBarsAppearance(dark: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    SideEffect {
        val window = view.context.findActivity()?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
