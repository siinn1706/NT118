package vn.uit.healthos.core.designsystem.motion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Checks the spring tokens against the motion table of the Liquid Glass plan: every timing and overshoot must sit
 * within 15 % of the value measured on iOS.
 */
class LiquidSpringsTest {

    @Test
    fun `tokens convert natural frequency and damping to a unit mass spring`() {
        val spec = LiquidSprings.OpenPlatter.spec<Float>()
        assertEquals(289f, spec.stiffness, 0.01f)
        assertEquals(0.76f, spec.dampingRatio, 0f)
        assertEquals(324f, LiquidSprings.ClosePlatter.stiffness, 0.01f)
        assertEquals(625f, LiquidSprings.WideTriggerCollapse.stiffness, 0.01f)
        assertEquals(169f, LiquidSprings.WideTriggerSplash.stiffness, 0.01f)
    }

    @Test
    fun `press and selection keep the springs the design system already used`() {
        assertEquals(700f, LiquidSprings.Press.stiffness, 0.01f)
        assertEquals(0.6f, LiquidSprings.Press.dampingRatio, 0f)
        assertEquals(420f, LiquidSprings.Selection.stiffness, 0.01f)
        assertEquals(0.78f, LiquidSprings.Selection.dampingRatio, 0f)
    }

    @Test
    fun `analytic oscillator starts at rest and ends on target for every damping regime`() {
        listOf(LiquidSpring(17f, 0.5f), LiquidSpring(18f, 1f), LiquidSpring(18f, 1.4f)).forEach { spring ->
            assertEquals(0.0, DampedOscillator.position(spring, 0.0), 1e-9)
            assertEquals(0.0, DampedOscillator.velocity(spring, 0.0), 1e-9)
            assertEquals(1.0, DampedOscillator.position(spring, 3.0), 1e-4)
            // Velocity is the derivative of position.
            val t = 0.1
            val numeric = (DampedOscillator.position(spring, t + 1e-6) - DampedOscillator.position(spring, t - 1e-6)) / 2e-6
            assertEquals(numeric, DampedOscillator.velocity(spring, t), 1e-3)
        }
    }

    @Test
    fun `open platter overshoots about 2_5 percent and settles near 420 ms`() {
        val spring = LiquidSprings.OpenPlatter
        val overshoot = DampedOscillator.peakOvershoot(spring)
        val settle = DampedOscillator.settleTime(spring, tolerance = 0.005)
        report("open overshoot", overshoot * 100, "%")
        report("open settle (0.5 %)", settle * 1000, "ms")
        assertWithin(0.025, overshoot)
        assertWithin(0.420, settle)
    }

    @Test
    fun `close platter reaches the trigger near 280 ms without visible overshoot`() {
        val spring = LiquidSprings.ClosePlatter
        val arrival = DampedOscillator.arrivalTime(spring)
        report("close arrival", arrival * 1000, "ms")
        report("close overshoot", DampedOscillator.peakOvershoot(spring) * 100, "%")
        assertWithin(0.280, arrival)
        assertTrue(DampedOscillator.peakOvershoot(spring) < 0.01)
    }

    @Test
    fun `wide trigger splash is full width near 320 ms and 1_6 percent wider near 400 ms`() {
        val spring = LiquidSprings.WideTriggerSplash
        val arrival = DampedOscillator.arrivalTime(spring)
        val peakTime = DampedOscillator.peakTime(spring)
        val overshoot = DampedOscillator.peakOvershoot(spring)
        report("splash full width", arrival * 1000, "ms")
        report("splash peak", peakTime * 1000, "ms")
        report("splash overshoot", overshoot * 100, "%")
        assertWithin(0.320, arrival)
        assertWithin(0.400, peakTime)
        assertWithin(0.016, overshoot)
    }

    @Test
    fun `wide trigger collapse is near critical and done within a quarter second`() {
        val spring = LiquidSprings.WideTriggerCollapse
        val settle = DampedOscillator.settleTime(spring, tolerance = 0.02)
        report("collapse settle (2 %)", settle * 1000, "ms")
        assertTrue(DampedOscillator.peakOvershoot(spring) < 0.001)
        assertTrue(settle < 0.25)
    }

    @Test
    fun `close recoil peaks near 183 ms and settles by 550 ms`() {
        val peakTime = CloseRecoil.peakTime
        val settle = CloseRecoil.settleTime(tolerance = 0.01)
        report("recoil peak", peakTime * 1000, "ms")
        report("recoil raw peak", CloseRecoil.peak, "")
        report("recoil settle (1 % of peak)", settle * 1000, "ms")
        assertWithin(0.183, peakTime)
        assertEquals(1.0, CloseRecoil.normalized(peakTime), 1e-9)
        assertTrue("recoil settles at $settle s", settle <= 0.550)
        assertTrue(settle >= 0.540 * 0.85)
        // The recoil only pushes forward: it never pulls back by more than 2 % of its peak.
        var t = 0.0
        while (t < 1.5) {
            assertTrue(CloseRecoil.normalized(t) > -0.02)
            t += 0.001
        }
    }

    private fun assertWithin(expected: Double, actual: Double, fraction: Double = 0.15) {
        assertTrue("expected $expected +-${fraction * 100}% but was $actual", abs(actual - expected) <= expected * fraction)
    }

    private fun report(label: String, value: Double, unit: String) {
        println("[motion] $label = ${"%.2f".format(value)} $unit")
    }
}
