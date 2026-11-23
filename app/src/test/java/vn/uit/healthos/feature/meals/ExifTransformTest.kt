package vn.uit.healthos.feature.meals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** EXIF orientation values 1-8 as defined by the EXIF 2.3 spec. */
class ExifTransformTest {

    @Test
    fun mapsEveryExifOrientationToItsUprightTransform() {
        val expected = mapOf(
            1 to ExifTransform(0, mirror = false),
            2 to ExifTransform(0, mirror = true),
            3 to ExifTransform(180, mirror = false),
            4 to ExifTransform(180, mirror = true),
            5 to ExifTransform(90, mirror = true),
            6 to ExifTransform(90, mirror = false),
            7 to ExifTransform(270, mirror = true),
            8 to ExifTransform(270, mirror = false),
        )
        expected.forEach { (orientation, transform) ->
            assertEquals("orientation $orientation", transform, exifTransformFor(orientation))
        }
    }

    @Test
    fun portraitCameraPhotoIsRotatedClockwise() {
        // Phone cameras store portrait shots landscape with orientation 6 (rotate 90 degrees clockwise).
        assertEquals(ExifTransform(90, mirror = false), exifTransformFor(6))
    }

    @Test
    fun missingOrUnknownOrientationLeavesPixelsAlone() {
        assertTrue(exifTransformFor(0).isIdentity)
        assertTrue(exifTransformFor(42).isIdentity)
        assertTrue(exifTransformFor(1).isIdentity)
    }
}
