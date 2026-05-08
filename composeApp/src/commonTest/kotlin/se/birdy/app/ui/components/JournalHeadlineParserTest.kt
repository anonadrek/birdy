package se.birdy.app.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

class JournalHeadlineParserTest {
    @Test
    fun `plain text returns single plain segment`() {
        val r = parseJournalHeadline("Birds.")
        assertEquals(listOf(HeadlineSegment.Plain("Birds.")), r)
    }

    @Test
    fun `accent at start splits to accent then plain`() {
        val r = parseJournalHeadline("*Twelve* found.")
        assertEquals(
            listOf(
                HeadlineSegment.Accent("Twelve"),
                HeadlineSegment.Plain(" found."),
            ),
            r,
        )
    }

    @Test
    fun `accent in middle splits to plain accent`() {
        val r = parseJournalHeadline("Three ways to *catch.*")
        assertEquals(
            listOf(
                HeadlineSegment.Plain("Three ways to "),
                HeadlineSegment.Accent("catch."),
            ),
            r,
        )
    }

    @Test
    fun `unmatched single asterisk is literal`() {
        val r = parseJournalHeadline("a*b")
        assertEquals(listOf(HeadlineSegment.Plain("a*b")), r)
    }

    @Test
    fun `empty pair is dropped`() {
        val r = parseJournalHeadline("a**b")
        assertEquals(listOf(HeadlineSegment.Plain("ab")), r)
    }

    @Test
    fun `two accents in same string both render`() {
        val r = parseJournalHeadline("*Twelve* found, *one* missed.")
        assertEquals(
            listOf(
                HeadlineSegment.Accent("Twelve"),
                HeadlineSegment.Plain(" found, "),
                HeadlineSegment.Accent("one"),
                HeadlineSegment.Plain(" missed."),
            ),
            r,
        )
    }

    @Test
    fun `escaped asterisk renders as literal asterisk`() {
        val r = parseJournalHeadline("Five \\* stars")
        assertEquals(listOf(HeadlineSegment.Plain("Five * stars")), r)
    }
}
