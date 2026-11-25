package vn.uit.healthos.core.designsystem.motion

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Closed-form step response of a unit-mass damped spring released at rest from 0 towards 1, the same motion Compose
 * runs for a [LiquidSpring]. Times are in seconds. Used to check the tokens against the measured iOS curves and to
 * normalise the close recoil.
 */
object DampedOscillator {

    /** Position at [t] seconds (0 at rest, 1 at the target). */
    fun position(spring: LiquidSpring, t: Double): Double {
        if (t <= 0.0) return 0.0
        val w = spring.naturalFrequency.toDouble()
        val z = spring.dampingRatio.toDouble()
        return when {
            z < 1.0 -> {
                val wd = w * sqrt(1.0 - z * z)
                val s = z * w
                1.0 - exp(-s * t) * (cos(wd * t) + s / wd * sin(wd * t))
            }
            z == 1.0 -> 1.0 - exp(-w * t) * (1.0 + w * t)
            else -> {
                val root = sqrt(z * z - 1.0)
                val r1 = -w * (z - root)
                val r2 = -w * (z + root)
                1.0 + (r2 * exp(r1 * t) - r1 * exp(r2 * t)) / (r1 - r2)
            }
        }
    }

    /** Velocity at [t] seconds, in target units per second. */
    fun velocity(spring: LiquidSpring, t: Double): Double {
        if (t <= 0.0) return 0.0
        val w = spring.naturalFrequency.toDouble()
        val z = spring.dampingRatio.toDouble()
        return when {
            z < 1.0 -> {
                val wd = w * sqrt(1.0 - z * z)
                exp(-z * w * t) * (w * w / wd) * sin(wd * t)
            }
            z == 1.0 -> w * w * t * exp(-w * t)
            else -> {
                val root = sqrt(z * z - 1.0)
                val r1 = -w * (z - root)
                val r2 = -w * (z + root)
                r1 * r2 * (exp(r1 * t) - exp(r2 * t)) / (r1 - r2)
            }
        }
    }

    /** Largest overshoot past the target as a fraction of the travel (0 when the spring is not underdamped). */
    fun peakOvershoot(spring: LiquidSpring): Double {
        val z = spring.dampingRatio.toDouble()
        if (z >= 1.0) return 0.0
        return exp(-z * PI / sqrt(1.0 - z * z))
    }

    /** Time of the first (largest) overshoot peak, or infinity when the spring never overshoots. */
    fun peakTime(spring: LiquidSpring): Double {
        val z = spring.dampingRatio.toDouble()
        if (z >= 1.0) return Double.POSITIVE_INFINITY
        return PI / (spring.naturalFrequency * sqrt(1.0 - z * z))
    }

    /** First time the motion reaches the target within [tolerance] (fraction of the travel). */
    fun arrivalTime(spring: LiquidSpring, tolerance: Double = 0.0): Double =
        firstTime { position(spring, it) >= 1.0 - tolerance }

    /** Time after which the motion stays within [tolerance] (fraction of the travel) of the target. */
    fun settleTime(spring: LiquidSpring, tolerance: Double): Double =
        settleTimeOf(tolerance) { position(spring, it) - 1.0 }

    internal fun firstTime(predicate: (Double) -> Boolean): Double {
        var t = 0.0
        while (t <= HORIZON) {
            if (predicate(t)) return t
            t += STEP
        }
        return Double.POSITIVE_INFINITY
    }

    internal fun settleTimeOf(tolerance: Double, error: (Double) -> Double): Double {
        var last = 0.0
        var t = 0.0
        while (t <= HORIZON) {
            if (abs(error(t)) > tolerance) last = t + STEP
            t += STEP
        }
        return last
    }

    private const val HORIZON = 3.0
    private const val STEP = 0.0005
}

/**
 * The small push a trigger takes when its platter lands back in it: two springs released together towards the same
 * target, S(17, 0.70) minus S(18, 1.0). The difference swells, peaks near 183 ms and returns to rest; [normalized]
 * scales it so the peak is 1, ready to multiply by a distance or a squash.
 */
object CloseRecoil {
    val drive: LiquidSpring = LiquidSprings.RecoilDrive
    val damp: LiquidSpring = LiquidSprings.RecoilDamp

    /** Unscaled difference of the two springs at [t] seconds. */
    fun raw(t: Double): Double = DampedOscillator.position(drive, t) - DampedOscillator.position(damp, t)

    /** Seconds from release to the largest push. */
    val peakTime: Double

    /** Largest raw difference, used to normalise. */
    val peak: Double

    init {
        var bestT = 0.0
        var best = 0.0
        var t = 0.0
        while (t <= 1.0) {
            val v = raw(t)
            if (v > best) {
                best = v
                bestT = t
            }
            t += 0.0005
        }
        peakTime = bestT
        peak = best
    }

    /** Recoil at [t] seconds with the peak scaled to 1. */
    fun normalized(t: Double): Double = raw(t) / peak

    /** Time after which the recoil stays within [tolerance] of rest, as a fraction of the peak. */
    fun settleTime(tolerance: Double = 0.01): Double =
        DampedOscillator.settleTimeOf(tolerance) { normalized(it) }
}
