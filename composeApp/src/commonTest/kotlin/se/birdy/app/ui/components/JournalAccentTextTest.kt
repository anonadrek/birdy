package se.birdy.app.ui.components

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Release 1.3.0 Plan 3 Task 7 (device walkthrough, API 36 emulator): two strings with `*accent*`
 * markup were shown with plain Text, so the asterisks reached the screen: "Håll telefonen stilla
 * och *låt den sjunga*" on Listen and "NYTT *MÄRKE*!" on the badge sheet.
 */
class JournalAccentTextTest {
    private val accent = Color(0xFF9A4526)

    @Test
    fun `the accent words get the accent colour and the asterisks are gone`() {
        val annotated = journalAccentAnnotated("Håll telefonen stilla och *låt den sjunga*", accent)

        assertEquals("Håll telefonen stilla och låt den sjunga", annotated.text)
        val span = annotated.spanStyles.single()
        assertEquals(accent, span.item.color)
        assertEquals("låt den sjunga", annotated.text.substring(span.start, span.end))
    }

    @Test
    fun `text without markup comes back unchanged and without spans`() {
        val annotated = journalAccentAnnotated("Inga stjärnor här", accent)

        assertEquals("Inga stjärnor här", annotated.text)
        assertEquals(0, annotated.spanStyles.size)
    }

    @Test
    fun `the plain version drops the markup`() {
        assertEquals("Nytt märke!", journalPlainText("Nytt *märke*!"))
        assertEquals("New badge!", journalPlainText("New *badge*!"))
    }
}
