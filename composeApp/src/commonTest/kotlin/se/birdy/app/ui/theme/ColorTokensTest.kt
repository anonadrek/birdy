package se.birdy.app.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

// Pins the "Mossa, rost & mässing" palette (1.3.0, spec 2026-09-24 §4.1). Previously pinned
// the Plan 7c Mossbädd palette (locked 2026-05-10) — updated 2026-09-24 for the palette lift;
// token NAMES are unchanged, only the literal values below moved to match the new spec.
class ColorTokensTest {
    @Test
    fun `paper top is light parchment`() {
        assertEquals(0xFFF8F2E7.toInt(), PaperTop.argb())
    }

    @Test
    fun `paper bottom is darker parchment`() {
        assertEquals(0xFFF2E9D8.toInt(), PaperBottom.argb())
    }

    @Test
    fun `accent copper is rust`() {
        assertEquals(0xFF9A4526.toInt(), AccentCopper.argb())
    }

    @Test
    fun `stamp locked is decorative tan`() {
        assertEquals(0xFFCDBB9C.toInt(), StampLocked.argb())
    }

    @Test
    fun `stamp unlocked bg is 12 percent rust`() {
        assertEquals(0x1F9A4526.toInt(), StampUnlockedBg.argb())
    }

    @Test
    fun `marginalia border is rust`() {
        assertEquals(0xFF9A4526.toInt(), MarginaliaBorder.argb())
    }
}

private fun Color.argb(): Int = (this.value shr 32).toInt()
