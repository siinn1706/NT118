package vn.uit.healthos.feature.meals

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.math.max

/** How to turn stored pixels upright: rotate clockwise by [rotationDegrees], then mirror horizontally if [mirror]. */
internal data class ExifTransform(val rotationDegrees: Int, val mirror: Boolean) {
    val isIdentity: Boolean get() = rotationDegrees == 0 && !mirror
}

/** Maps an EXIF orientation tag to the transform that displays the image upright; unknown values mean upright. */
internal fun exifTransformFor(orientation: Int): ExifTransform = when (orientation) {
    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> ExifTransform(0, mirror = true)
    ExifInterface.ORIENTATION_ROTATE_180 -> ExifTransform(180, mirror = false)
    ExifInterface.ORIENTATION_FLIP_VERTICAL -> ExifTransform(180, mirror = true)
    ExifInterface.ORIENTATION_TRANSPOSE -> ExifTransform(90, mirror = true)
    ExifInterface.ORIENTATION_ROTATE_90 -> ExifTransform(90, mirror = false)
    ExifInterface.ORIENTATION_TRANSVERSE -> ExifTransform(270, mirror = true)
    ExifInterface.ORIENTATION_ROTATE_270 -> ExifTransform(270, mirror = false)
    else -> ExifTransform(0, mirror = false)
}

/**
 * Downscales any local photo URI to <= 1280 px on its longest side, applies its EXIF orientation so the pixels are
 * upright (the encoded JPEG carries no orientation tag), and encodes JPEG quality 85.
 */
object ImageDownscaler {
    private const val TAG = "ImageDownscaler"
    private const val MAX_DIMENSION = 1280
    private const val JPEG_QUALITY = 85

    /** Returns null when the URI cannot be read or decoded. */
    suspend fun downscaleToJpegBytes(context: Context, uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
        try {
            encodeUpright(context, uri)
        } catch (e: IOException) {
            Log.w(TAG, "Could not read photo", e)
            null
        } catch (e: SecurityException) {
            Log.w(TAG, "No permission to read photo", e)
            null
        }
    }

    private fun encodeUpright(context: Context, uri: Uri): ByteArray? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { stream -> BitmapFactory.decodeStream(stream, null, bounds) }
        val origW = bounds.outWidth
        val origH = bounds.outHeight
        if (origW <= 0 || origH <= 0) return null

        var sampleSize = 1
        while (max(origW / sampleSize, origH / sampleSize) > MAX_DIMENSION * 2) {
            sampleSize *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOpts)
        } ?: return null

        val transform = exifTransformFor(readOrientation(context, uri))

        val maxSide = max(decoded.width, decoded.height)
        val scale = if (maxSide > MAX_DIMENSION) MAX_DIMENSION.toFloat() / maxSide.toFloat() else 1f
        val upright = if (scale == 1f && transform.isIdentity) {
            decoded
        } else {
            val matrix = Matrix().apply {
                setScale(scale, scale)
                postRotate(transform.rotationDegrees.toFloat())
                if (transform.mirror) postScale(-1f, 1f)
            }
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also {
                if (it !== decoded) decoded.recycle()
            }
        }

        val out = ByteArrayOutputStream()
        upright.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        upright.recycle()
        return out.toByteArray()
    }

    /** An unreadable or missing EXIF block means the pixels are already stored upright. */
    private fun readOrientation(context: Context, uri: Uri): Int = try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
    } catch (e: IOException) {
        Log.w(TAG, "Could not read photo orientation", e)
        ExifInterface.ORIENTATION_NORMAL
    }
}
