package se.birdy.ml

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Golden byte-parity check for the CoreGraphics [ImagePreprocessor] iOS actual.
 *
 * The Android actual ([se.birdy.ml.ImagePreprocessor] androidMain) is the reference
 * implementation: RGB row-major, per-channel `out = (px/255 - mean) / std`, top-left
 * pixel first. This test pins that exact contract for iOS using a 2×2 fixture where
 * out-dims == in-dims, so there is **no resampling** and no color-management ambiguity
 * (all four pixels are pure primaries/white — endpoints that are identical in sRGB and
 * device-RGB). That isolates the normalization + channel-order + spatial-order plumbing
 * from scaling, which is inherently approximate across platforms.
 *
 * The bytes below are the canonical golden fixture: a 2×2 8-bit RGB PNG generated
 * deterministically (Python zlib, filter-0 scanlines, color type 2). They are embedded here
 * rather than loaded as a resource because the Compose resources plugin does not expose test
 * `composeResources` to Kotlin/Native test binaries. Fixture layout (row-major, top-to-bottom):
 *
 *   row0:  TL = red   (255,0,0)   TR = green (0,255,0)
 *   row1:  BL = blue  (0,0,255)   BR = white (255,255,255)
 */
class ImagePreprocessorIosTest {
    private val paritycardPng: ByteArray =
        ubyteArrayOf(
            0x89u,
            0x50u,
            0x4Eu,
            0x47u,
            0x0Du,
            0x0Au,
            0x1Au,
            0x0Au,
            0x00u,
            0x00u,
            0x00u,
            0x0Du,
            0x49u,
            0x48u,
            0x44u,
            0x52u,
            0x00u,
            0x00u,
            0x00u,
            0x02u,
            0x00u,
            0x00u,
            0x00u,
            0x02u,
            0x08u,
            0x02u,
            0x00u,
            0x00u,
            0x00u,
            0xFDu,
            0xD4u,
            0x9Au,
            0x73u,
            0x00u,
            0x00u,
            0x00u,
            0x12u,
            0x49u,
            0x44u,
            0x41u,
            0x54u,
            0x78u,
            0xDAu,
            0x63u,
            0xF8u,
            0xCFu,
            0xC0u,
            0xC0u,
            0x00u,
            0xC2u,
            0x0Cu,
            0xFFu,
            0x81u,
            0x00u,
            0x00u,
            0x1Fu,
            0xEEu,
            0x05u,
            0xFBu,
            0xF1u,
            0xABu,
            0xBAu,
            0x77u,
            0x00u,
            0x00u,
            0x00u,
            0x00u,
            0x49u,
            0x45u,
            0x4Eu,
            0x44u,
            0xAEu,
            0x42u,
            0x60u,
            0x82u,
        ).toByteArray()

    private val identityMean = floatArrayOf(0.5f, 0.5f, 0.5f)
    private val identityStd = floatArrayOf(0.5f, 0.5f, 0.5f)
    private val eps = 1e-3f

    /** `(channel/255 − 0.5)/0.5`: a full channel → +1, a zero channel → −1. */
    private fun n(v: Int): Float = (v / 255f - 0.5f) / 0.5f

    @Test
    fun preprocess_2x2_no_resample_byte_matches_android_normalization() {
        val input =
            ImageInput(
                bytes = paritycardPng,
                widthPx = 2,
                heightPx = 2,
                rotationDegrees = 0,
                format = FrameFormat.JPEG,
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

        // RGB row-major, top-left first: red, green, blue, white.
        val expected =
            floatArrayOf(
                n(255),
                n(0),
                n(0), // (0,0) red
                n(0),
                n(255),
                n(0), // (0,1) green
                n(0),
                n(0),
                n(255), // (1,0) blue
                n(255),
                n(255),
                n(255), // (1,1) white
            )
        for (i in expected.indices) {
            assertEquals(expected[i], out[i], eps, "float[$i] mismatch")
        }
    }

    @Test
    fun preprocess_applies_90deg_clockwise_rotation_exactly() {
        // 2×2 → 2×2 rotation is a lossless remap (no scaling), so it is byte-exact.
        // Rotating the fixture 90° clockwise:
        //   R G          B R
        //   B W    ->    W G
        val input =
            ImageInput(
                bytes = paritycardPng,
                widthPx = 2,
                heightPx = 2,
                rotationDegrees = 90,
                format = FrameFormat.JPEG,
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

        val expected =
            floatArrayOf(
                n(0),
                n(0),
                n(255), // (0,0) blue
                n(255),
                n(0),
                n(0), // (0,1) red
                n(255),
                n(255),
                n(255), // (1,0) white
                n(0),
                n(255),
                n(0), // (1,1) green
            )
        assertEquals(2 * 2 * 3, out.size)
        for (i in expected.indices) {
            assertEquals(expected[i], out[i], eps, "rotated float[$i] mismatch")
        }
    }
}
