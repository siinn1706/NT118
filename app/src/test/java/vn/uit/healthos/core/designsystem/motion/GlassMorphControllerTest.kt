package vn.uit.healthos.core.designsystem.motion

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Runs the real controller (Animatables on a coroutine scope) against a virtual frame clock ticking every millisecond
 * and measures the timings the plan's motion table asks for, within 15 %.
 */
class GlassMorphControllerTest {

    @Test
    fun `open flies out with a small overshoot and settles near 420 ms`() = runTest {
        val controller = controller()
        val start = testScheduler.currentTime
        controller.open()
        var peak = 0f
        var settledAt = 0L
        var detachingSeen = false
        sampleEachMs(700) { t ->
            peak = maxOf(peak, controller.pathT)
            if (abs(controller.pathT - 1f) > 0.005f) settledAt = t - start
            if (controller.phase == GlassMorphPhase.Detaching) detachingSeen = true
        }
        report("controller open overshoot", (peak - 1f) * 100.0, "%")
        report("controller open settle (0.5 %)", settledAt.toDouble(), "ms")
        assertWithin(0.025, (peak - 1f).toDouble())
        assertWithin(420.0, settledAt.toDouble())
        assertTrue(detachingSeen)
        assertEquals(GlassMorphPhase.Settled, controller.phase)
        assertEquals(0f, controller.triggerWidthProgress, 0.001f)
        assertEquals(1f, controller.platterScale, 0.002f)
        assertEquals(1f, controller.glassProgress, 0.001f)
        assertEquals(0f, controller.blendRadius.value, 0.001f)
    }

    @Test
    fun `close vanishes near 280 ms while the trigger splashes back and recoils`() = runTest {
        val controller = controller()
        controller.open()
        advanceUntilIdle()
        val start = testScheduler.currentTime
        controller.close()
        var vanishedAt = -1L
        var recoilPeak = 0f
        var recoilPeakAt = 0L
        var recoilSettledAt = 0L
        var widthFullAt = -1L
        var widthPeak = 0f
        var widthPeakAt = 0L
        var minScale = 1f
        var maxPush = 0f
        var minBlend = Float.MAX_VALUE
        var maxBlend = 0f
        sampleEachMs(900) { now ->
            val t = now - start
            if (vanishedAt < 0 && !controller.isShowing) vanishedAt = t
            if (controller.recoil > recoilPeak) {
                recoilPeak = controller.recoil
                recoilPeakAt = t
            }
            if (abs(controller.recoil) > 0.01f) recoilSettledAt = t
            if (widthFullAt < 0 && controller.triggerWidthProgress >= 1f) widthFullAt = t
            if (controller.triggerWidthProgress > widthPeak) {
                widthPeak = controller.triggerWidthProgress
                widthPeakAt = t
            }
            minScale = minOf(minScale, controller.triggerImpactScale)
            maxPush = maxOf(maxPush, controller.triggerPushDy)
            minBlend = minOf(minBlend, controller.blendRadius.value)
            maxBlend = maxOf(maxBlend, controller.blendRadius.value)
        }
        report("controller close vanish", vanishedAt.toDouble(), "ms")
        report("controller recoil peak", recoilPeakAt.toDouble(), "ms")
        report("controller recoil settle (1 %)", recoilSettledAt.toDouble(), "ms")
        report("controller splash full width", widthFullAt.toDouble(), "ms")
        report("controller splash peak", widthPeakAt.toDouble(), "ms")
        report("controller splash overshoot", (widthPeak - 1f) * 100.0, "%")
        report("controller impact scale", minScale.toDouble(), "")
        assertWithin(280.0, vanishedAt.toDouble())
        assertWithin(183.0, recoilPeakAt.toDouble())
        assertTrue("recoil settled at $recoilSettledAt ms", recoilSettledAt <= 550)
        assertWithin(320.0, widthFullAt.toDouble())
        assertWithin(400.0, widthPeakAt.toDouble())
        assertWithin(0.016, (widthPeak - 1f).toDouble())
        assertEquals(GlassMorphController.ImpactScale, minScale, 0.002f)
        assertEquals(RecoilPx, maxPush, 0.2f)
        assertEquals(0f, controller.triggerPushDx, 0f)
        assertTrue(minBlend >= 0f && maxBlend <= 28f)
        assertEquals(28f, maxBlend, 0.01f)
        assertEquals(GlassMorphPhase.Idle, controller.phase)
        assertEquals(GlassMorphController.ClosedPlatterScale, controller.platterScale, 0.002f)
        assertEquals(1f, controller.triggerImpactScale, 0.001f)
    }

    @Test
    fun `reduced motion snaps the springs and crossfades the glass in about 150 ms without recoil`() = runTest {
        val controller = controller(reducedMotion = true)
        val openStart = testScheduler.currentTime
        controller.open()
        runCurrent()
        assertEquals(1f, controller.pathT, 0f)
        assertEquals(0f, controller.triggerWidthProgress, 0f)
        var openFaded = -1L
        sampleEachMs(300) { now ->
            if (openFaded < 0 && controller.glassProgress >= 1f) openFaded = now - openStart
        }
        report("reduced open fade", openFaded.toDouble(), "ms")
        assertWithin(150.0, openFaded.toDouble())

        val closeStart = testScheduler.currentTime
        controller.close()
        runCurrent()
        assertEquals(1f, controller.triggerWidthProgress, 0f)
        var closeFaded = -1L
        var halfway = -1f
        sampleEachMs(300) { now ->
            val t = now - closeStart
            if (t == 75L) halfway = controller.glassProgress
            if (closeFaded < 0 && !controller.isShowing) closeFaded = t
            assertEquals(0f, controller.recoil, 0f)
            assertEquals(0f, controller.triggerPushDy, 0f)
            assertEquals(1f, controller.triggerImpactScale, 0f)
        }
        report("reduced close fade", closeFaded.toDouble(), "ms")
        assertWithin(150.0, closeFaded.toDouble())
        assertTrue("linear crossfade halfway was $halfway", halfway in 0.35f..0.65f)
        assertEquals(0f, controller.pathT, 0f)
        assertEquals(GlassMorphPhase.Idle, controller.phase)
    }

    @Test
    fun `reset jumps to the closed state from the middle of a morph`() = runTest {
        val controller = controller()
        controller.open()
        advanceTimeBy(120)
        runCurrent()
        assertTrue(controller.isShowing)
        controller.reset()
        advanceUntilIdle()
        assertFalse(controller.isOpen)
        assertFalse(controller.isShowing)
        assertEquals(0f, controller.pathT, 0f)
        assertEquals(1f, controller.triggerWidthProgress, 0f)
        assertEquals(GlassMorphPhase.Idle, controller.phase)
    }

    @Test
    fun `closing mid flight turns back from the current position`() = runTest {
        val controller = controller()
        controller.open()
        advanceTimeBy(100)
        runCurrent()
        val midway = controller.pathT
        assertTrue(midway in 0.2f..0.95f)
        controller.close()
        advanceTimeBy(2)
        runCurrent()
        // No jump: the platter continues from where it was.
        assertTrue(abs(controller.pathT - midway) < 0.1f)
        advanceUntilIdle()
        assertEquals(0f, controller.pathT, 0.001f)
    }

    @Test
    fun `phases follow the direction of travel`() {
        assertEquals(GlassMorphPhase.Idle, morphPhase(opening = false, pathT = 0f, animating = false))
        assertEquals(GlassMorphPhase.Settled, morphPhase(opening = true, pathT = 1f, animating = false))
        assertEquals(GlassMorphPhase.Detaching, morphPhase(opening = true, pathT = 0.1f, animating = true))
        assertEquals(GlassMorphPhase.Travelling, morphPhase(opening = true, pathT = 0.5f, animating = true))
        assertEquals(GlassMorphPhase.Arriving, morphPhase(opening = true, pathT = 1.02f, animating = true))
        assertEquals(GlassMorphPhase.Detaching, morphPhase(opening = false, pathT = 0.9f, animating = true))
        assertEquals(GlassMorphPhase.Arriving, morphPhase(opening = false, pathT = 0.05f, animating = true))
    }

    @Test
    fun `blend radius stays between 0 and 28 dp and fades out by half the travel`() {
        var p = -0.2f
        while (p <= 1.2f) {
            val r = morphBlendRadius(p)
            assertTrue("blend $r at $p", r >= 0.dp && r <= 28.dp)
            p += 0.01f
        }
        assertEquals(28f, morphBlendRadius(0f).value, 0f)
        assertEquals(0f, morphBlendRadius(0.5f).value, 0f)
        assertTrue(morphBlendRadius(0.1f) > morphBlendRadius(0.3f))
    }

    @Test
    fun `j curve rises out of the trigger before swinging across`() {
        val from = Offset(300f, 600f)
        val to = Offset(100f, 200f)
        assertEquals(from, jCurve(from, to, 0f))
        assertEquals(to, jCurve(from, to, 1f))
        val early = jCurve(from, to, 0.25f)
        // A quarter of the way, it has climbed more than it has moved sideways.
        assertTrue(abs(early.y - from.y) > abs(early.x - from.x))
    }

    private fun TestScope.controller(reducedMotion: Boolean = false) = GlassMorphController(
        scope = CoroutineScope(coroutineContext + MillisecondFrameClock(testScheduler)),
        reducedMotion = reducedMotion,
        recoilDistancePx = RecoilPx,
        recoilDirection = Offset(0f, 1f),
    )

    private fun TestScope.sampleEachMs(durationMs: Int, sample: (now: Long) -> Unit) {
        repeat(durationMs) {
            advanceTimeBy(1)
            runCurrent()
            sample(testScheduler.currentTime)
        }
    }

    private fun assertWithin(expected: Double, actual: Double, fraction: Double = 0.15) {
        assertTrue("expected $expected +-${fraction * 100}% but was $actual", abs(actual - expected) <= expected * fraction)
    }

    private fun report(label: String, value: Double, unit: String) {
        println("[motion] $label = ${"%.2f".format(value)} $unit")
    }

    /** Frame clock on virtual time: one frame per millisecond, stamped with the scheduler's clock. */
    private class MillisecondFrameClock(private val scheduler: TestCoroutineScheduler) : MonotonicFrameClock {
        override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
            delay(1)
            return onFrame(scheduler.currentTime * 1_000_000L)
        }
    }

    private companion object {
        const val RecoilPx = 12f
    }
}
