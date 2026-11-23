package vn.uit.healthos.feature.safety

import kotlin.math.abs
import kotlin.math.sqrt

data class FallDetectorConfig(
    val freeFallThresholdG: Float = 0.5f,
    val freeFallMinDurationMs: Long = 80L,
    val impactThresholdG: Float = 2.5f,
    val impactWindowMs: Long = 1_000L,
    val postImpactSettleMs: Long = 400L,
    val stillnessToleranceG: Float = 0.35f,
    val stillnessDurationMs: Long = 2_000L,
    val cooldownMs: Long = 10_000L,
) {
    companion object {
        val STANDARD = FallDetectorConfig()
        val TEST_MODE = FallDetectorConfig(
            freeFallThresholdG = 0.65f,
            freeFallMinDurationMs = 40L,
            impactThresholdG = 1.8f,
            impactWindowMs = 1_200L,
            postImpactSettleMs = 300L,
            stillnessToleranceG = 0.45f,
            stillnessDurationMs = 1_200L,
            cooldownMs = 5_000L,
        )
    }
}

sealed interface FallDetectionEvent {
    data object None : FallDetectionEvent
    data class FallSuspected(val peakImpactG: Float, val detectedAtMs: Long) : FallDetectionEvent
}

/**
 * Pure 3-stage accelerometer fall detector (no Android dependencies):
 * 1. Free-fall: |a| < freeFallThresholdG continuously for >= freeFallMinDurationMs
 * 2. Impact: |a| > impactThresholdG within impactWindowMs of free-fall
 * 3. Stillness: after a brief bounce settle, |a| stays within 1.0 ± stillnessToleranceG for stillnessDurationMs
 */
class FallDetector(
    private var config: FallDetectorConfig = FallDetectorConfig.STANDARD,
) {
    private enum class Phase { IDLE, IN_FREE_FALL, AWAITING_IMPACT, CONFIRMING_STILLNESS }

    private var phase = Phase.IDLE
    private var freeFallStartMs = 0L
    private var freeFallEndMs = 0L
    private var impactAtMs = 0L
    private var peakImpactG = 0f
    private var stillnessStartMs: Long? = null
    private var cooldownUntilMs = 0L

    fun updateConfig(newConfig: FallDetectorConfig) {
        config = newConfig
        reset()
    }

    fun reset() {
        phase = Phase.IDLE
        freeFallStartMs = 0L
        freeFallEndMs = 0L
        impactAtMs = 0L
        peakImpactG = 0f
        stillnessStartMs = null
    }

    /** Feed raw accelerometer readings in m/s² along with a monotonic timestamp in ms. */
    fun onAccelerometerSample(ax: Float, ay: Float, az: Float, timestampMs: Long): FallDetectionEvent {
        val magnitudeG = sqrt(ax * ax + ay * ay + az * az) / GRAVITY_MS2
        return onMagnitudeG(magnitudeG, timestampMs)
    }

    fun onMagnitudeG(magnitudeG: Float, timestampMs: Long): FallDetectionEvent {
        if (timestampMs < cooldownUntilMs) return FallDetectionEvent.None

        when (phase) {
            Phase.IDLE -> {
                if (magnitudeG < config.freeFallThresholdG) {
                    phase = Phase.IN_FREE_FALL
                    freeFallStartMs = timestampMs
                }
            }

            Phase.IN_FREE_FALL -> {
                if (magnitudeG < config.freeFallThresholdG) {
                    // Stay in free-fall.
                } else {
                    val duration = timestampMs - freeFallStartMs
                    if (duration >= config.freeFallMinDurationMs) {
                        freeFallEndMs = timestampMs
                        if (magnitudeG >= config.impactThresholdG) {
                            enterImpact(magnitudeG, timestampMs)
                        } else {
                            phase = Phase.AWAITING_IMPACT
                        }
                    } else {
                        phase = Phase.IDLE
                    }
                }
            }

            Phase.AWAITING_IMPACT -> {
                if (timestampMs - freeFallEndMs > config.impactWindowMs) {
                    phase = Phase.IDLE
                } else if (magnitudeG >= config.impactThresholdG) {
                    enterImpact(magnitudeG, timestampMs)
                }
            }

            Phase.CONFIRMING_STILLNESS -> {
                if (magnitudeG > peakImpactG) peakImpactG = magnitudeG
                val sinceImpact = timestampMs - impactAtMs
                if (sinceImpact < config.postImpactSettleMs) {
                    return FallDetectionEvent.None
                }
                val isStill = abs(magnitudeG - 1.0f) <= config.stillnessToleranceG
                if (isStill) {
                    val start = stillnessStartMs ?: timestampMs.also { stillnessStartMs = it }
                    if (timestampMs - start >= config.stillnessDurationMs) {
                        val peak = peakImpactG
                        reset()
                        cooldownUntilMs = timestampMs + config.cooldownMs
                        return FallDetectionEvent.FallSuspected(peakImpactG = peak, detectedAtMs = timestampMs)
                    }
                } else if (sinceImpact <= config.postImpactSettleMs + 600L) {
                    // Brief residual bounce right after impact: restart stillness timer without aborting.
                    stillnessStartMs = null
                } else {
                    // Significant movement well after impact -> person got up or picked up phone.
                    reset()
                }
            }
        }
        return FallDetectionEvent.None
    }

    private fun enterImpact(magnitudeG: Float, timestampMs: Long) {
        phase = Phase.CONFIRMING_STILLNESS
        impactAtMs = timestampMs
        peakImpactG = magnitudeG
        stillnessStartMs = null
    }

    companion object {
        const val GRAVITY_MS2 = 9.80665f
    }
}
