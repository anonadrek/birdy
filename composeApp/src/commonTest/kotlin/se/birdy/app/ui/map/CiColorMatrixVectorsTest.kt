package se.birdy.app.ui.map

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class CiColorMatrixVectorsTest {
    /** CI-modellen: out.r = dot(in, r) + bias[0], allt i 0..1. */
    private fun apply(
        v: CiColorMatrixVectors,
        rgba: FloatArray,
    ): FloatArray {
        fun dot(vec: FloatArray) = vec[0] * rgba[0] + vec[1] * rgba[1] + vec[2] * rgba[2] + vec[3] * rgba[3]
        return floatArrayOf(dot(v.r) + v.bias[0], dot(v.g) + v.bias[1], dot(v.b) + v.bias[2], dot(v.a) + v.bias[3])
    }

    private fun assertClose(
        expected: Float,
        actual: Float,
    ) = assertTrue(abs(expected - actual) < 0.005f, "expected $expected got $actual")

    @Test
    fun white_maps_to_paper() {
        val v = ciVectorsFrom(MapTileTheme.duotoneMatrix(MapTileTheme.INK, MapTileTheme.PAPER))
        val out = apply(v, floatArrayOf(1f, 1f, 1f, 1f))
        assertClose(0xEF / 255f, out[0])
        assertClose(0xE7 / 255f, out[1])
        assertClose(0xD6 / 255f, out[2])
    }

    @Test
    fun black_maps_to_ink() {
        val v = ciVectorsFrom(MapTileTheme.duotoneMatrix(MapTileTheme.INK, MapTileTheme.PAPER))
        val out = apply(v, floatArrayOf(0f, 0f, 0f, 1f))
        assertClose(0x2E / 255f, out[0])
        assertClose(0x24 / 255f, out[1])
        assertClose(0x17 / 255f, out[2])
    }

    @Test
    fun alpha_passes_through() {
        val v = ciVectorsFrom(MapTileTheme.duotoneMatrix(MapTileTheme.INK, MapTileTheme.PAPER))
        assertClose(0.5f, apply(v, floatArrayOf(0.3f, 0.3f, 0.3f, 0.5f))[3])
    }
}
