package vn.uit.healthos.feature.ppg

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class PpgSample(
    val timestampMs: Long,
    val value: Double,
    val spatialStdDev: Double = 0.0,
)

enum class PpgQuality {
    NO_FINGER,
    COLLECTING,
    LOW_QUALITY,
    GOOD,
}

data class PpgAnalysisSnapshot(
    val quality: PpgQuality,
    val bpm: Int?,
    val confidence: Float,
    /** Length of the current contiguous finger-covered segment. */
    val elapsedSeconds: Float,
    val waveform: List<Float>,
    val ibiCv: Double? = null,
)

data class PpgConfig(
    val sampleRateHz: Double = 30.0,
    val windowSeconds: Double = 20.0,
    val minAnalysisSeconds: Double = 6.0,
    val highPassHz: Double = 0.7,
    val lowPassHz: Double = 3.5,
    val refractorySeconds: Double = 0.33,
    val minFingerMean: Double = 135.0,
    val maxFingerSpatialStd: Double = 32.0,
    val minSignalRms: Double = 0.08,
    val maxIbiCv: Double = 0.15,
    val maxCrestFactor: Double = 4.2,
    /** A longer pause between frames breaks the contiguous segment (dropped frames, camera hiccup). */
    val maxFrameGapMs: Long = 500,
)

/**
 * Pure Kotlin photoplethysmography (PPG) signal processor:
 * resamples irregular camera frames to 30 Hz → 1 s moving-average detrend →
 * 2nd-order Butterworth band-pass (0.7–3.5 Hz) → refractory peak detection (0.33 s) →
 * median inter-beat interval BPM + signal quality index (SQI).
 *
 * Only a contiguous finger-covered segment is analysed: a frame without a finger, a frame gap or a timestamp going
 * backwards discards the window, so [PpgAnalysisSnapshot.elapsedSeconds] is the length of the current good segment.
 *
 * Not thread-safe: callers must confine all calls to one thread or synchronize externally.
 */
class PpgSignalProcessor(
    private val config: PpgConfig = PpgConfig(),
) {
    private val samples = ArrayDeque<PpgSample>()
    private var segmentStartMs: Long? = null

    fun reset() {
        samples.clear()
        segmentStartMs = null
    }

    fun addSample(sample: PpgSample): PpgAnalysisSnapshot {
        val fingerPresent = sample.value >= config.minFingerMean &&
            sample.spatialStdDev <= config.maxFingerSpatialStd
        if (!fingerPresent) {
            reset()
            return PpgAnalysisSnapshot(
                quality = PpgQuality.NO_FINGER,
                bpm = null,
                confidence = 0f,
                elapsedSeconds = 0f,
                waveform = emptyList(),
            )
        }

        val previous = samples.lastOrNull()
        if (previous != null) {
            val gapMs = sample.timestampMs - previous.timestampMs
            if (gapMs <= 0L || gapMs > config.maxFrameGapMs) reset()
        }
        val segmentStart = segmentStartMs ?: sample.timestampMs.also { segmentStartMs = it }
        samples.addLast(sample)

        val windowMs = (config.windowSeconds * 1_000.0).toLong()
        while (samples.size > 1 && (sample.timestampMs - samples.first().timestampMs) > windowMs) {
            samples.removeFirst()
        }

        val elapsedSec = ((sample.timestampMs - segmentStart) / 1_000.0).toFloat()

        val spanSec = (samples.last().timestampMs - samples.first().timestampMs) / 1_000.0
        if (samples.size < 30 || spanSec < 2.0) {
            return PpgAnalysisSnapshot(
                quality = PpgQuality.COLLECTING,
                bpm = null,
                confidence = 0f,
                elapsedSeconds = elapsedSec,
                waveform = emptyList(),
            )
        }

        val uniform = resampleUniform(samples.toList(), config.sampleRateHz)
        val detrended = detrendMovingAverage(uniform, windowSamples = config.sampleRateHz.roundToInt().coerceAtLeast(5))
        val hp = BiquadFilter.highPass(config.highPassHz, config.sampleRateHz).filter(detrended)
        val filtered = BiquadFilter.lowPass(config.lowPassHz, config.sampleRateHz).filter(hp)

        val transient = (config.sampleRateHz * 1.2).roundToInt().coerceAtMost(filtered.size / 3)
        val usable = if (filtered.size > transient + 15) {
            filtered.subList(transient, filtered.size)
        } else {
            filtered
        }

        val waveform = normalizeWaveform(usable.takeLast(120))

        if (spanSec < config.minAnalysisSeconds) {
            return PpgAnalysisSnapshot(
                quality = PpgQuality.COLLECTING,
                bpm = null,
                confidence = 0f,
                elapsedSeconds = elapsedSec,
                waveform = waveform,
            )
        }

        val rms = sqrt(usable.sumOf { it * it } / usable.size.coerceAtLeast(1))
        if (rms < config.minSignalRms) {
            return PpgAnalysisSnapshot(
                quality = PpgQuality.LOW_QUALITY,
                bpm = null,
                confidence = 0f,
                elapsedSeconds = elapsedSec,
                waveform = waveform,
            )
        }

        val maxAbs = usable.maxOf { abs(it) }
        val crestFactor = maxAbs / rms
        if (crestFactor > config.maxCrestFactor) {
            return PpgAnalysisSnapshot(
                quality = PpgQuality.LOW_QUALITY,
                bpm = null,
                confidence = 0f,
                elapsedSeconds = elapsedSec,
                waveform = waveform,
            )
        }

        val refractorySamples = (config.refractorySeconds * config.sampleRateHz).roundToInt().coerceAtLeast(1)
        val peaks = detectPeaks(usable, minHeight = rms * 0.35, refractorySamples = refractorySamples)
        if (peaks.size < 5) {
            return PpgAnalysisSnapshot(
                quality = PpgQuality.LOW_QUALITY,
                bpm = null,
                confidence = 0f,
                elapsedSeconds = elapsedSec,
                waveform = waveform,
            )
        }

        val ibis = buildList {
            for (i in 1 until peaks.size) {
                val sec = (peaks[i] - peaks[i - 1]) / config.sampleRateHz
                if (sec in 0.30..1.50) add(sec)
            }
        }
        if (ibis.size < 4) {
            return PpgAnalysisSnapshot(
                quality = PpgQuality.LOW_QUALITY,
                bpm = null,
                confidence = 0f,
                elapsedSeconds = elapsedSec,
                waveform = waveform,
            )
        }

        val meanIbi = ibis.average()
        val stdIbi = sqrt(ibis.sumOf { (it - meanIbi) * (it - meanIbi) } / ibis.size)
        val cv = if (meanIbi > 0.0) stdIbi / meanIbi else 1.0
        if (cv >= config.maxIbiCv) {
            return PpgAnalysisSnapshot(
                quality = PpgQuality.LOW_QUALITY,
                bpm = null,
                confidence = 0f,
                elapsedSeconds = elapsedSec,
                waveform = waveform,
                ibiCv = cv,
            )
        }

        val sorted = ibis.sorted()
        val medianIbi = if (sorted.size % 2 == 1) {
            sorted[sorted.size / 2]
        } else {
            (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
        }
        val bpm = (60.0 / medianIbi).roundToInt()
        if (bpm !in 40..200) {
            return PpgAnalysisSnapshot(
                quality = PpgQuality.LOW_QUALITY,
                bpm = null,
                confidence = 0f,
                elapsedSeconds = elapsedSec,
                waveform = waveform,
                ibiCv = cv,
            )
        }

        val confidence = (1.0 - (cv / config.maxIbiCv)).toFloat().coerceIn(0.1f, 1.0f)
        return PpgAnalysisSnapshot(
            quality = PpgQuality.GOOD,
            bpm = bpm,
            confidence = confidence,
            elapsedSeconds = elapsedSec,
            waveform = waveform,
            ibiCv = cv,
        )
    }

    private fun resampleUniform(input: List<PpgSample>, sampleRateHz: Double): List<Double> {
        val t0 = input.first().timestampMs.toDouble()
        val tEnd = input.last().timestampMs.toDouble()
        val stepMs = 1_000.0 / sampleRateHz
        val count = (((tEnd - t0) / stepMs).toInt() + 1).coerceAtLeast(2)
        val out = ArrayList<Double>(count)
        var j = 0
        for (i in 0 until count) {
            val t = t0 + i * stepMs
            while (j < input.lastIndex - 1 && input[j + 1].timestampMs < t) {
                j++
            }
            val a = input[j]
            val b = input[(j + 1).coerceAtMost(input.lastIndex)]
            val span = (b.timestampMs - a.timestampMs).toDouble()
            val v = if (span <= 0.0) {
                a.value
            } else {
                val frac = ((t - a.timestampMs) / span).coerceIn(0.0, 1.0)
                a.value + (b.value - a.value) * frac
            }
            out.add(v)
        }
        return out
    }

    private fun detrendMovingAverage(signal: List<Double>, windowSamples: Int): List<Double> {
        val half = windowSamples / 2
        val prefix = DoubleArray(signal.size + 1)
        for (i in signal.indices) prefix[i + 1] = prefix[i] + signal[i]
        return List(signal.size) { i ->
            val lo = (i - half).coerceAtLeast(0)
            val hi = (i + half).coerceAtMost(signal.lastIndex)
            val avg = (prefix[hi + 1] - prefix[lo]) / (hi - lo + 1)
            signal[i] - avg
        }
    }

    private fun detectPeaks(signal: List<Double>, minHeight: Double, refractorySamples: Int): List<Int> {
        val peaks = mutableListOf<Int>()
        for (i in 1 until signal.lastIndex) {
            val cur = signal[i]
            if (cur >= minHeight && cur > signal[i - 1] && cur >= signal[i + 1]) {
                val last = peaks.lastOrNull()
                if (last == null || (i - last) >= refractorySamples) {
                    peaks.add(i)
                } else if (cur > signal[last]) {
                    peaks[peaks.lastIndex] = i
                }
            }
        }
        return peaks
    }

    private fun normalizeWaveform(segment: List<Double>): List<Float> {
        if (segment.isEmpty()) return emptyList()
        val maxAbs = segment.maxOf { abs(it) }.coerceAtLeast(1e-6)
        return segment.map { (it / maxAbs).toFloat().coerceIn(-1f, 1f) }
    }
}

/** 2nd-order Butterworth IIR biquad filter (Audio EQ Cookbook bilinear transform). */
internal class BiquadFilter(
    private val b0: Double,
    private val b1: Double,
    private val b2: Double,
    private val a1: Double,
    private val a2: Double,
) {
    fun filter(input: List<Double>): List<Double> {
        val out = ArrayList<Double>(input.size)
        var x1 = 0.0
        var x2 = 0.0
        var y1 = 0.0
        var y2 = 0.0
        for (x0 in input) {
            val y0 = b0 * x0 + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            out.add(y0)
            x2 = x1
            x1 = x0
            y2 = y1
            y1 = y0
        }
        return out
    }

    companion object {
        private const val BUTTERWORTH_Q = 0.7071067811865476

        fun lowPass(cutoffHz: Double, sampleRateHz: Double): BiquadFilter {
            val w0 = 2.0 * PI * cutoffHz / sampleRateHz
            val cosW0 = cos(w0)
            val alpha = sin(w0) / (2.0 * BUTTERWORTH_Q)
            val a0 = 1.0 + alpha
            return BiquadFilter(
                b0 = ((1.0 - cosW0) / 2.0) / a0,
                b1 = (1.0 - cosW0) / a0,
                b2 = ((1.0 - cosW0) / 2.0) / a0,
                a1 = (-2.0 * cosW0) / a0,
                a2 = (1.0 - alpha) / a0,
            )
        }

        fun highPass(cutoffHz: Double, sampleRateHz: Double): BiquadFilter {
            val w0 = 2.0 * PI * cutoffHz / sampleRateHz
            val cosW0 = cos(w0)
            val alpha = sin(w0) / (2.0 * BUTTERWORTH_Q)
            val a0 = 1.0 + alpha
            return BiquadFilter(
                b0 = ((1.0 + cosW0) / 2.0) / a0,
                b1 = -(1.0 + cosW0) / a0,
                b2 = ((1.0 + cosW0) / 2.0) / a0,
                a1 = (-2.0 * cosW0) / a0,
                a2 = (1.0 - alpha) / a0,
            )
        }
    }
}
