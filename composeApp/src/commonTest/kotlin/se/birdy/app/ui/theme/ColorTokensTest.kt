package se.birdy.app.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

class ColorTokensTest {
    @Test
    fun `paper top is light parchment`() {
        assertEquals(0xFFF0E7D0.toInt(), PaperTop.argb())
    }

    @Test
    fun `paper bottom is darker parchment`() {
        assertEquals(0xFFE6D8B8.toInt(), PaperBottom.argb())
    }

    @Test
    fun `stamp locked is 40 percent copper`() {
        assertEquals(0x668C5A3C.toInt(), StampLocked.argb())
    }

    @Test
    fun `stamp unlocked bg is 12 percent copper`() {
        assertEquals(0x1F8C5A3C.toInt(), StampUnlockedBg.argb())
    }

    @Test
    fun `marginalia border is copper`() {
        assertEquals(0xFF8C5A3C.toInt(), MarginaliaBorder.argb())
    }
}

private fun Color.argb(): Int = (this.value shr 32).toInt()
