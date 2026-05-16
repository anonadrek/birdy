package se.birdy.app.ui.match

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MatchOverrideParserTest {
    @Test fun `parses valid Q-id and confidence`() {
        val result = parseMatchOverride("Q25356:0.42")
        assertEquals(MatchOverride("Q25356", 0.42f), result)
    }

    @Test fun `returns null on missing colon`() {
        assertNull(parseMatchOverride("Q25356"))
    }

    @Test fun `returns null on non-Q id`() {
        assertNull(parseMatchOverride("X25356:0.42"))
    }

    @Test fun `returns null on non-numeric confidence`() {
        assertNull(parseMatchOverride("Q25356:high"))
    }

    @Test fun `returns null on confidence out of range`() {
        assertNull(parseMatchOverride("Q25356:1.5"))
        assertNull(parseMatchOverride("Q25356:-0.1"))
    }

    @Test fun `trims whitespace`() {
        assertEquals(MatchOverride("Q25356", 0.5f), parseMatchOverride("  Q25356 : 0.5  \n"))
    }

    @Test fun `accepts boundaries`() {
        assertEquals(MatchOverride("Q1", 0.0f), parseMatchOverride("Q1:0.0"))
        assertEquals(MatchOverride("Q1", 1.0f), parseMatchOverride("Q1:1.0"))
    }
}
