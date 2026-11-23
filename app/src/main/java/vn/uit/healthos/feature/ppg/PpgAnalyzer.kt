package vn.uit.healthos.feature.ppg

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import kotlin.math.sqrt

/**
 * Extracts the mean and spatial standard deviation of the V (Cr / red-chroma) plane in the center
 * 50% ROI of each YUV_420_888 frame and emits a [PpgSample].
 */
class PpgAnalyzer(
    private val onSample: (PpgSample) -> Unit,
) : ImageAnalysis.Analyzer {

    override fun analyze(image: ImageProxy) {
        try {
            if (image.planes.size < 3) return
            val vPlane = image.planes[2]
            val buffer = vPlane.buffer
            val rowStride = vPlane.rowStride
            val pixelStride = vPlane.pixelStride
            val chromaWidth = image.width / 2
            val chromaHeight = image.height / 2
            if (chromaWidth <= 4 || chromaHeight <= 4) return

            val xStart = chromaWidth / 4
            val xEnd = (chromaWidth * 3) / 4
            val yStart = chromaHeight / 4
            val yEnd = (chromaHeight * 3) / 4

            var sum = 0.0
            var sumSq = 0.0
            var count = 0
            val capacity = buffer.capacity()

            var y = yStart
            while (y < yEnd) {
                val rowOffset = y * rowStride
                var x = xStart
                while (x < xEnd) {
                    val idx = rowOffset + x * pixelStride
                    if (idx in 0 until capacity) {
                        val v = (buffer.get(idx).toInt() and 0xFF).toDouble()
                        sum += v
                        sumSq += v * v
                        count++
                    }
                    x += 2
                }
                y += 2
            }

            if (count > 0) {
                val mean = sum / count
                val variance = (sumSq / count - mean * mean).coerceAtLeast(0.0)
                val tsMs = image.imageInfo.timestamp / 1_000_000L
                onSample(PpgSample(timestampMs = tsMs, value = mean, spatialStdDev = sqrt(variance)))
            }
        } finally {
            image.close()
        }
    }
}
