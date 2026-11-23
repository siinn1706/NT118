package vn.uit.healthos.feature.safety

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FallDetectorTest {

    @Test
    fun realFallTraceTriggersFallSuspected() {
        val detector = FallDetector(FallDetectorConfig.STANDARD)
        var t = 0L
        var triggered = false

        // 1. Calm standing (1.0 g)
        repeat(10) {
            assertTrue(detector.onMagnitudeG(1.0f, t) is FallDetectionEvent.None)
            t += 20L
        }
        // 2. Free-fall (< 0.5 g) for 120 ms (6 samples @ 50 Hz)
        repeat(6) {
            assertTrue(detector.onMagnitudeG(0.25f, t) is FallDetectionEvent.None)
            t += 20L
        }
        // 3. Impact spike (> 2.5 g)
        detector.onMagnitudeG(3.4f, t)
        t += 20L
        // 4. Bounce settle (400 ms)
        repeat(20) {
            detector.onMagnitudeG(1.4f, t)
            t += 20L
        }
        // 5. Post-impact stillness (~1.0 g) for 2.2 s
        repeat(110) {
            val ev = detector.onMagnitudeG(1.02f, t)
            if (ev is FallDetectionEvent.FallSuspected) triggered = true
            t += 20L
        }
        assertTrue("Expected real fall trace to trigger FallSuspected", triggered)
    }

    @Test
    fun sittingDownHardDoesNotTriggerBecauseNoFreeFall() {
        val detector = FallDetector(FallDetectorConfig.STANDARD)
        var t = 0L
        var triggered = false
        // Brief dip above threshold (0.7 g), then 2.8 g sit impact, then still
        repeat(5) {
            detector.onMagnitudeG(0.7f, t)
            t += 20L
        }
        detector.onMagnitudeG(2.8f, t)
        t += 20L
        repeat(140) {
            if (detector.onMagnitudeG(1.0f, t) is FallDetectionEvent.FallSuspected) triggered = true
            t += 20L
        }
        assertFalse("Sitting down hard without free-fall must not trigger", triggered)
    }

    @Test
    fun normalWalkingTraceDoesNotTrigger() {
        val detector = FallDetector(FallDetectorConfig.STANDARD)
        var t = 0L
        var triggered = false
        val gaitCycle = listOf(0.85f, 1.15f, 1.45f, 1.1f, 0.9f, 0.78f, 1.35f, 1.05f)
        repeat(300) { idx ->
            val g = gaitCycle[idx % gaitCycle.size]
            if (detector.onMagnitudeG(g, t) is FallDetectionEvent.FallSuspected) triggered = true
            t += 20L
        }
        assertFalse("Normal walking must not trigger fall detection", triggered)
    }

    @Test
    fun phoneDroppedThenPickedUpImmediatelyDoesNotTrigger() {
        val detector = FallDetector(FallDetectorConfig.STANDARD)
        var t = 0L
        var triggered = false
        // Free-fall 120 ms
        repeat(6) {
            detector.onMagnitudeG(0.2f, t)
            t += 20L
        }
        // Impact 3.2 g
        detector.onMagnitudeG(3.2f, t)
        t += 20L
        // Settle 400 ms + 600 ms still, then user picks up phone (1.8 g movement)
        repeat(50) {
            if (detector.onMagnitudeG(1.0f, t) is FallDetectionEvent.FallSuspected) triggered = true
            t += 20L
        }
        detector.onMagnitudeG(1.8f, t)
        t += 20L
        repeat(120) {
            if (detector.onMagnitudeG(1.0f, t) is FallDetectionEvent.FallSuspected) triggered = true
            t += 20L
        }
        assertFalse("Phone picked up within 2s must cancel fall detection", triggered)
    }
}
