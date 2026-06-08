package se.birdy.app.ui.map

import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals

class MapTileThemeTest {
    // Applies a 4x5 ColorMatrix to an opaque (r,g,b) pixel, returns clamped RGB ints.
    private fun apply(
        m: FloatArray,
        r: Int,
        g: Int,
        b: Int,
    ): Triple<Int, Int, Int> {
        fun ch(o: Int) =
            (m[o] * r + m[o + 1] * g + m[o + 2] * b + m[o + 3] * 255 + m[o + 4])
                .roundToInt()
                .coerceIn(0, 255)
        return Triple(ch(0), ch(5), ch(10))
    }

    private fun rgb(c: Int) = Triple((c shr 16) and 0xFF, (c shr 8) and 0xFF, c and 0xFF)

    @Test
    fun blackMapsToInk() {
        val m = MapTileTheme.duotoneMatrix(ink = 0x2E2417, paper = 0xEFE7D6)
        assertEquals(rgb(0x2E2417), apply(m, 0, 0, 0))
    }

    @Test
    fun whiteMapsToPaper() {
        val m = MapTileTheme.duotoneMatrix(ink = 0x2E2417, paper = 0xEFE7D6)
        assertEquals(rgb(0xEFE7D6), apply(m, 255, 255, 255))
    }

    @Test
    fun midGrayLandsBetweenInkAndPaper() {
        val m = MapTileTheme.duotoneMatrix(ink = 0x000000, paper = 0xFFFFFF)
        val (r, g, b) = apply(m, 128, 128, 128)
        // luminance of pure gray 128 == 128; duotone of black..white == identity gray
        assertEquals(128, r)
        assertEquals(128, g)
        assertEquals(128, b)
    }
}
