package se.birdy.ml

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the BGRA_8888 → RGB float contract for the iOS [ImagePreprocessor] (i2c).
 * BGRA is AVCaptureVideoDataOutput's native layout; feeding it through the RGBA path
 * produces silently channel-swapped inference — this test makes that regression loud.
 * Same 2×2 primaries fixture as [ImagePreprocessorIosTest], but as raw BGRA bytes.
 */
class ImagePreprocessorBgraTest {
    // Row-major BGRA: TL red, TR green, BL blue, BR white.
    private val bgraBytes: ByteArray =
        ubyteArrayOf(
            0x00u,
            0x00u,
            0xFFu,
            0xFFu, // (0,0) red   = B0 G0 R255 A255
            0x00u,
            0xFFu,
            0x00u,
            0xFFu, // (0,1) green
            0xFFu,
            0x00u,
            0x00u,
            0xFFu, // (1,0) blue
            0xFFu,
            0xFFu,
            0xFFu,
            0xFFu, // (1,1) white
        ).toByteArray()

    private val identityMean = floatArrayOf(0.5f, 0.5f, 0.5f)
    private val identityStd = floatArrayOf(0.5f, 0.5f, 0.5f)
    private val eps = 1e-3f

    private fun n(v: Int): Float = (v / 255f - 0.5f) / 0.5f

    @Test
    fun preprocess_bgra_2x2_matches_rgba_reference_floats() {
        val input =
            ImageInput(
                bytes = bgraBytes,
                widthPx = 2,
                heightPx = 2,
                rotationDegrees = 0,
                format = FrameFormat.BGRA_8888,
                timestampMillis = 0L,
            )

        val out =
            ImagePreprocessor().preprocess(
                input,
                outHeight = 2,
                outWidth = 2,
                normalizationMean = identityMean,
                normalizationStd = identityStd,
            )

        assertEquals(2 * 2 * 3, out.size, "must be outH*outW*3 floats")
        // RGB row-major, top-left first: red, green, blue, white — identical to the RGBA/JPEG
        // reference in ImagePreprocessorIosTest. A B/R swap turns red into blue and fails loudly.
        val expected =
            floatArrayOf(
                n(255),
                n(0),
                n(0),
                n(0),
                n(255),
                n(0),
                n(0),
                n(0),
                n(255),
                n(255),
                n(255),
                n(255),
            )
        for (i in expected.indices) {
            assertEquals(expected[i], out[i], eps, "float[$i] mismatch")
        }
    }
}
