package vn.uit.healthos.core.designsystem.motion

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Immutable
import kotlin.math.sqrt

/**
 * A spring described the way motion designers measure it: natural frequency [naturalFrequency] (ω₀, rad/s) and
 * damping ratio [dampingRatio] (ζ). With unit mass Compose's stiffness is ω₀², so a token converts to a [SpringSpec]
 * without retuning. [DampedOscillator] gives the analytic curve of the same spring.
 */
@Immutable
data class LiquidSpring(val naturalFrequency: Float, val dampingRatio: Float) {

    init {
        require(naturalFrequency > 0f) { "naturalFrequency must be positive" }
        require(dampingRatio > 0f) { "dampingRatio must be positive" }
    }

    /** Compose stiffness for a unit mass: k = ω₀². */
    val stiffness: Float get() = naturalFrequency * naturalFrequency

    fun <T> spec(visibilityThreshold: T? = null): SpringSpec<T> =
        spring(dampingRatio = dampingRatio, stiffness = stiffness, visibilityThreshold = visibilityThreshold)

    companion object {
        /** Token for an existing Compose spring given as stiffness and damping ratio. */
        fun fromStiffness(stiffness: Float, dampingRatio: Float): LiquidSpring =
            LiquidSpring(sqrt(stiffness), dampingRatio)
    }
}

/**
 * Spring tokens for Liquid Glass motion. The platter and trigger values come from a morph engine calibrated against
 * 60 fps traces of iOS; [Press] and [Selection] are the springs the design system already used (VbMotion.press and
 * VbMotion.snappy) written as ω₀/ζ.
 */
object LiquidSprings {
    /** Platter flying out of its trigger along a J curve: about 2.5 % overshoot, settled near 420 ms. */
    val OpenPlatter = LiquidSpring(17f, 0.76f)

    /** Platter pulled back into the centre of its trigger: reaches it near 280 ms with almost no overshoot. */
    val ClosePlatter = LiquidSpring(18f, 0.86f)

    /** Wide trigger shrinking to a circle in place while its platter opens. */
    val WideTriggerCollapse = LiquidSpring(25f, 0.94f)

    /** Wide trigger growing back when the platter closes: full width near 320 ms, +1.6 % at about 400 ms. */
    val WideTriggerSplash = LiquidSpring(13f, 0.795f)

    /** Driving half of the close recoil, S(17, 0.70). */
    val RecoilDrive = LiquidSpring(17f, 0.70f)

    /** Damping half of the close recoil, S(18, 1.0); the recoil is RecoilDrive minus RecoilDamp. */
    val RecoilDamp = LiquidSpring(18f, 1.0f)

    /** Press feedback on glass, same feel as VbMotion.press (stiffness 700, ζ 0.6). */
    val Press = LiquidSpring.fromStiffness(700f, 0.6f)

    /** Selection and indicators, same feel as VbMotion.snappy (stiffness 420, ζ 0.78). */
    val Selection = LiquidSpring.fromStiffness(420f, 0.78f)
}
