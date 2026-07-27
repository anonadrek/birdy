@file:OptIn(ExperimentalForeignApi::class)

package se.birdy.app.ui.scan

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import se.birdy.ml.FrameFormat
import se.birdy.ml.ImageInput
import se.birdy.ml.ImagePreprocessor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Freeze-encode-kontraktet (i2c): BGRA-frame-bytes → upprätt JPEG med bevarade färger
 * och dimensioner. Färgkontrollen avkodar via shared/ml:s ImagePreprocessor (JPEG-vägen)
 * med identitetsnormalisering (mean=0, std=1/255 → floats == pixelbytes).
 */
class IosScanFramePersistTest {
    // 64×32: vänster halva röd, höger halva blå — BGRA-layout (B,G,R,A).
    private fun testFrame(): ImageInput {
        val w = 64
        val h = 32
        val bytes = ByteArray(w * h * 4)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val o = (y * w + x) * 4
                if (x < w / 2) {
                    bytes[o + 2] = 0xFF.toByte() // R
                } else {
                    bytes[o] = 0xFF.toByte() // B
                }
                bytes[o + 3] = 0xFF.toByte()
            }
        }
        return ImageInput(bytes, widthPx = w, heightPx = h, format = FrameFormat.BGRA_8888, timestampMillis = 1L)
    }

    private fun decodedPixel(
        jpeg: ByteArray,
        w: Int,
        h: Int,
        x: Int,
        y: Int,
    ): Triple<Int, Int, Int> {
        val floats =
            ImagePreprocessor().preprocess(
                ImageInput(jpeg, w, h, format = FrameFormat.JPEG),
                outHeight = h,
                outWidth = w,
                normalizationMean = floatArrayOf(0f, 0f, 0f),
                normalizationStd = floatArrayOf(1 / 255f, 1 / 255f, 1 / 255f),
            )
        val o = (y * w + x) * 3
        return Triple(floats[o].toInt(), floats[o + 1].toInt(), floats[o + 2].toInt())
    }

    @Test
    fun encode_preserves_dimensions_and_channel_order() {
        val jpeg = encodeBgraFrameToJpeg(testFrame())
        assertNotNull(jpeg, "encode returned null")
        // Sampla mitt i varje halva (JPEG-artefakter vid kanterna — tolerans 40/255).
        val (lr, lg, lb) = decodedPixel(jpeg, 64, 32, x = 16, y = 16)
        val (rr, rg, rb) = decodedPixel(jpeg, 64, 32, x = 48, y = 16)
        assertTrue(lr > 200 && lb < 60, "left half must decode red-ish, got rgb($lr,$lg,$lb)")
        assertTrue(rb > 200 && rr < 60, "right half must decode blue-ish, got rgb($rr,$rg,$rb)")
    }

    @Test
    fun persist_writes_a_readable_jpg_and_returns_its_path() {
        val path = persistScanFrame(testFrame())
        assertTrue(path.endsWith(".jpg"), "expected .jpg path, got $path")
        assertTrue(NSFileManager.defaultManager.fileExistsAtPath(path), "file must exist at $path")
        NSFileManager.defaultManager.removeItemAtPath(path, null)
    }

    @Test
    fun encode_rejects_wrong_size_and_wrong_format() {
        val bad = ImageInput(ByteArray(10), widthPx = 64, heightPx = 32, format = FrameFormat.BGRA_8888)
        assertEquals(null, encodeBgraFrameToJpeg(bad))
        val jpegInput = ImageInput(ByteArray(100), widthPx = 5, heightPx = 5, format = FrameFormat.JPEG)
        assertEquals(null, encodeBgraFrameToJpeg(jpegInput))
    }
}
