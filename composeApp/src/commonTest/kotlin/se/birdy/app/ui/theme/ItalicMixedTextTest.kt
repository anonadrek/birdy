package se.birdy.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import kotlin.test.Test
import kotlin.test.assertEquals

class ItalicMixedTextTest {
    private val accent = Color(0xFFE0A47C)

    @Test
    fun `parses single italic segment`() {
        val result = parseItalicMixed("Three ways to *catch*.", accent)
        assertEquals("Three ways to catch.", result.text)
        val spans = result.spanStyles
        assertEquals(1, spans.size)
        assertEquals(14, spans[0].start) // "Three ways to "
        assertEquals(19, spans[0].end) // "catch"
        assertEquals(FontStyle.Italic, spans[0].item.fontStyle)
        assertEquals(accent, spans[0].item.color)
    }

    @Test
    fun `parses two italic segments`() {
        val result = parseItalicMixed("*Albin's* secret *list*.", accent)
        assertEquals("Albin's secret list.", result.text)
        assertEquals(2, result.spanStyles.size)
    }

    @Test
    fun `no asterisks returns plain text with no spans`() {
        val result = parseItalicMixed("Plain text only.", accent)
        assertEquals("Plain text only.", result.text)
        assertEquals(0, result.spanStyles.size)
    }

    @Test
    fun `unmatched asterisk is treated as literal`() {
        val result = parseItalicMixed("Half-italic *open", accent)
        assertEquals("Half-italic *open", result.text)
        assertEquals(0, result.spanStyles.size)
    }

    @Test
    fun `empty asterisk pair produces no span`() {
        val result = parseItalicMixed("Hello ** world.", accent)
        assertEquals("Hello  world.", result.text)
        assertEquals(0, result.spanStyles.size)
    }

    @Test
    fun `escaped asterisk via backslash is literal`() {
        val result = parseItalicMixed("5 \\* 3 = 15", accent)
        assertEquals("5 * 3 = 15", result.text)
        assertEquals(0, result.spanStyles.size)
    }
}
