package vn.uit.healthos.core.designsystem.theme

import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
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
}

/** True when the user turned animations off in system settings (Remove animations). */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}
