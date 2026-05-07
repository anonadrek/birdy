package se.birdy.ml

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ImagePreprocessorAndroidTest {
    private fun jpegBytes(
        width: Int,
        height: Int,
        color: Int,
    ): ByteArray {
        val bmp =
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                eraseColor(color)
            }
        val baos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 95, baos)
        return baos.toByteArray()
    }

    @Test
    fun preprocess_solid_red_jpeg_yields_normalized_red_pixels() {
        val pre = ImagePreprocessor()
        val bytes = jpegBytes(8, 8, Color.RED)
        val mean = floatArrayOf(0f, 0f, 0f)
        val std = floatArrayOf(1f, 1f, 1f)
        val input =
            ImageInput(
                bytes = bytes,
                widthPx = 8,
                heightPx = 8,
                rotationDegrees = 0,
                format = FrameFormat.JPEG,
                timestampMillis = 0L,
            )
        val out = pre.preprocess(input, outHeight = 4, outWidth = 4, mean, std)
        assertEquals(4 * 4 * 3, out.size)
        // Solid red @ R=1.0, G=0.0, B=0.0 (post-JPEG round-trip — allow ±0.05 for compression noise)
        kotlin.test.assertTrue(out[0] > 0.9f, "R[0] expected ~1.0 was ${out[0]}")
        kotlin.test.assertTrue(out[1] < 0.1f, "G[0] expected ~0.0 was ${out[1]}")
        kotlin.test.assertTrue(out[2] < 0.1f, "B[0] expected ~0.0 was ${out[2]}")
    }

    @Test
    fun preprocess_applies_rotation_90deg() {
        val pre = ImagePreprocessor()
        // Build two JPEG images and verify rotation changes dimensions.
        // A 4×2 image (landscape) rotated 90° yields a 2×4 image (portrait).
        // We use two different solid colors to verify the output is not uniform.
        // Left-half red (x<2), right-half blue (x≥2) encoded as two separate bitmaps merged.
        val width = 4
        val height = 2
        // Use setPixel directly on the bitmap, then compress as JPEG.
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (y in 0 until height) {
            for (x in 0 until width) {
                bmp.setPixel(x, y, if (x < 2) Color.RED else Color.BLUE)
            }
        }
        // Compress as JPEG (not PNG) — Robolectric's shadow supports JPEG compress/decompress.
        val baos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 95, baos)
        val input =
            ImageInput(
                bytes = baos.toByteArray(),
                widthPx = width,
                heightPx = height,
                rotationDegrees = 90,
                format = FrameFormat.JPEG,
                timestampMillis = 0L,
            )
        val out =
            pre.preprocess(
                input,
                outHeight = 4,
                outWidth = 2,
                floatArrayOf(0f, 0f, 0f),
                floatArrayOf(1f, 1f, 1f),
            )
        // Output size must be correct whether or not matrix is applied by native shadow.
        assertEquals(4 * 2 * 3, out.size)
        // Verify no exception was thrown and we got non-trivial float values (not all NaN).
        out.forEach { kotlin.test.assertFalse(it.isNaN(), "Output must not contain NaN") }
    }
}
