package vn.uit.healthos.core.designsystem.theme

import android.content.ContentResolver
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

object VbMotion {
    const val SHORT_MS = 200
    const val MEDIUM_MS = 250
    const val LONG_MS = 300

    fun <T> enter(): FiniteAnimationSpec<T> = tween(durationMillis = MEDIUM_MS)
    fun <T> exit(): FiniteAnimationSpec<T> = tween(durationMillis = SHORT_MS)
    fun <T> bouncy(): FiniteAnimationSpec<T> =
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
    fun <T> gentle(): FiniteAnimationSpec<T> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

    /** Apple-style "snappy" spring: quick settle with a hint of overshoot, used for selection and indicators. */
    fun <T> snappy(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.78f, stiffness = 420f)

    /** Press feedback for glass surfaces: fast in, slightly springy out. */
    fun <T> press(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.6f, stiffness = 700f)

    /** [spec] normally, an instant jump when the user asked the system to remove animations. */
    fun <T> orSnap(reducedMotion: Boolean, spec: FiniteAnimationSpec<T>): FiniteAnimationSpec<T> =
        if (reducedMotion) snap() else spec
}

/**
 * True while the user has turned animations off (Settings > Accessibility > Remove animations, which sets the
 * animator duration scale to 0). Provided by HealthOsTheme and kept up to date while the app runs, so every spring in
 * the design system can snap instead of bouncing.
 */
val LocalReducedMotion = compositionLocalOf { false }

/** True when the user asked for opaque surfaces (in-app setting or the system high-contrast text setting). */
val LocalReducedTransparency = compositionLocalOf { false }

/** Current reduced-motion flag; kept for existing call sites, reads [LocalReducedMotion]. */
@Composable
fun rememberReducedMotion(): Boolean = LocalReducedMotion.current

/** Live system "Remove animations" flag, observed through a ContentObserver. */
@Composable
internal fun observeSystemReducedMotion(): State<Boolean> = rememberSettingState(
    uri = Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
) { resolver ->
    Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
}

/** Live system "High contrast text" flag. Reading it can be refused on some ROMs, which counts as off. */
@Composable
internal fun observeSystemHighTextContrast(): State<Boolean> = rememberSettingState(
    uri = runCatching { Settings.Secure.getUriFor(HIGH_TEXT_CONTRAST) }.getOrNull(),
) { resolver ->
    runCatching { Settings.Secure.getInt(resolver, HIGH_TEXT_CONTRAST, 0) == 1 }.getOrDefault(false)
}

private const val HIGH_TEXT_CONTRAST = "high_text_contrast_enabled"

@Composable
private fun rememberSettingState(uri: Uri?, read: (ContentResolver) -> Boolean): State<Boolean> {
    val resolver = LocalContext.current.contentResolver
    val state = remember(resolver) { mutableStateOf(read(resolver)) }
    DisposableEffect(resolver, uri) {
        if (uri == null) return@DisposableEffect onDispose {}
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                state.value = read(resolver)
            }
        }
        resolver.registerContentObserver(uri, false, observer)
        // The value may have changed between the first read and registration.
        state.value = read(resolver)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return state
}
