package vn.uit.healthos.feature.ppg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

class PpgSignalProcessorTest {

    @Test
    fun synthetic72BpmSineWithNoiseAndDriftProducesAccurateBpm() {
        val processor = PpgSignalProcessor()
        val rng = Random(42)
        val freqHz = 1.2 // 72 BPM
        var last: PpgAnalysisSnapshot? = null

        // 15 seconds at ~30 fps with slight frame jitter, baseline drift, and white noise
        for (i in 0 until 450) {
            val tSec = i / 30.0
            val jitterMs = (rng.nextDouble(-2.0, 2.0)).toLong()
            val tsMs = (tSec * 1_000.0).toLong() + jitterMs
            val baselineDrift = 195.0 + 3.0 * sin(2.0 * PI * 0.08 * tSec)
            val pulse = 4.5 * sin(2.0 * PI * freqHz * tSec)
            val noise = rng.nextDouble(-0.4, 0.4)
            last = processor.addSample(PpgSample(timestampMs = tsMs, value = baselineDrift + pulse + noise, spatialStdDev = 6.0))
        }

        assertNotNull(last)
        assertEquals(PpgQuality.GOOD, last?.quality)
        val bpm = last?.bpm
        assertNotNull(bpm)
        assertTrue("Expected 72 ± 2 BPM, got $bpm", bpm!! in 70..74)
    }

    @Test
    fun flatSignalIsRejectedAsLowQuality() {
        val processor = PpgSignalProcessor()
        var last: PpgAnalysisSnapshot? = null
        for (i in 0 until 360) {
            val tsMs = (i * 1000L) / 30L
            last = processor.addSample(PpgSample(timestampMs = tsMs, value = 190.0, spatialStdDev = 5.0))
        }
        assertEquals(PpgQuality.LOW_QUALITY, last?.quality)
        assertNull(last?.bpm)
    }

    @Test
    fun signalWithMotionSpikesIsRejected() {
        val processor = PpgSignalProcessor()
        var last: PpgAnalysisSnapshot? = null
        for (i in 0 until 400) {
            val tSec = i / 30.0
            val tsMs = (tSec * 1_000.0).toLong()
            val spike = if (i == 220 || i == 221) 45.0 else 0.0
            val value = 195.0 + 1.5 * sin(2.0 * PI * 1.2 * tSec) + spike
            last = processor.addSample(PpgSample(timestampMs = tsMs, value = value, spatialStdDev = 7.0))
        }
        assertEquals(PpgQuality.LOW_QUALITY, last?.quality)
        assertNull(last?.bpm)
    }

    @Test
    fun uncoveredLensReturnsNoFinger() {
        val processor = PpgSignalProcessor()
        val snap = processor.addSample(PpgSample(timestampMs = 1_000L, value = 110.0, spatialStdDev = 45.0))
        assertEquals(PpgQuality.NO_FINGER, snap.quality)
        assertNull(snap.bpm)
    }

    private fun fingerSample(tSec: Double, freqHz: Double) = PpgSample(
        timestampMs = (tSec * 1_000.0).toLong(),
        value = 195.0 + 4.5 * sin(2.0 * PI * freqHz * tSec),
        spatialStdDev = 6.0,
    )

    @Test
    fun liftingTheFingerDropsTheWindowAndRestartsTheSegment() {
        val processor = PpgSignalProcessor()
        var tSec = 0.0
        repeat(300) { // 10 s at 72 BPM
            processor.addSample(fingerSample(tSec, freqHz = 1.2))
            tSec += 1 / 30.0
        }
        repeat(60) { // 2 s with the lens uncovered
            val snap = processor.addSample(
                PpgSample(timestampMs = (tSec * 1_000.0).toLong(), value = 110.0, spatialStdDev = 45.0),
            )
            assertEquals(PpgQuality.NO_FINGER, snap.quality)
            assertEquals(0f, snap.elapsedSeconds)
            tSec += 1 / 30.0
        }

        val firstBack = processor.addSample(fingerSample(tSec, freqHz = 1.5))
        assertEquals(PpgQuality.COLLECTING, firstBack.quality)
        assertEquals(0f, firstBack.elapsedSeconds)
        tSec += 1 / 30.0

        var last: PpgAnalysisSnapshot = firstBack
        repeat(420) { // 14 s at 90 BPM
            last = processor.addSample(fingerSample(tSec, freqHz = 1.5))
            tSec += 1 / 30.0
        }

        assertEquals(PpgQuality.GOOD, last.quality)
        val bpm = last.bpm
        assertNotNull(bpm)
        assertTrue("Expected 90 ± 2 BPM from the new segment only, got $bpm", bpm!! in 88..92)
        assertTrue(last.elapsedSeconds in 13.5f..14.5f)
    }

    @Test
    fun frameGapRestartsTheSegment() {
        val processor = PpgSignalProcessor()
        var tSec = 0.0
        repeat(240) {
            processor.addSample(fingerSample(tSec, freqHz = 1.2))
            tSec += 1 / 30.0
        }

        val afterGap = processor.addSample(fingerSample(tSec + 1.0, freqHz = 1.2))

        assertEquals(PpgQuality.COLLECTING, afterGap.quality)
        assertEquals(0f, afterGap.elapsedSeconds)
    }

    @Test
    fun segmentLengthKeepsGrowingPastTheAnalysisWindow() {
        val processor = PpgSignalProcessor()
        var last: PpgAnalysisSnapshot? = null
        for (i in 0 until 750) { // 25 s, window is 20 s
            last = processor.addSample(fingerSample(i / 30.0, freqHz = 1.2))
        }

        assertTrue("elapsed was ${last?.elapsedSeconds}", (last?.elapsedSeconds ?: 0f) >= 24.9f)
        assertEquals(PpgQuality.GOOD, last?.quality)
    }
}
