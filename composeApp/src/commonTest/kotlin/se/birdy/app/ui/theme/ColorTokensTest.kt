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
    fun `accent copper is retinted warm copper`() {
        assertEquals(0xFFA8552D.toInt(), AccentCopper.argb())
    }

    @Test
    fun `stamp locked is 40 percent copper`() {
        assertEquals(0x66A8552D.toInt(), StampLocked.argb())
    }

    @Test
    fun `stamp unlocked bg is 12 percent copper`() {
        assertEquals(0x1FA8552D.toInt(), StampUnlockedBg.argb())
    }

    @Test
    fun `marginalia border is copper`() {
        assertEquals(0xFFA8552D.toInt(), MarginaliaBorder.argb())
    }
}

private fun Color.argb(): Int = (this.value shr 32).toInt()
