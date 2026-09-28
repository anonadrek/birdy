package se.birdy.app.ui.premium

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [thanksHeadlineParts] must split `*accent*` markup into a trailing accent — that's the only
 * shape [se.birdy.app.ui.components.PhotoHero]'s `title`/`titleAccent` slots can render (accent
 * always appended after the title, never mid-sentence). See the function's KDoc.
 */
class PremiumThankYouHeadlineTest {
    @Test
    fun `plain text with a trailing accent splits into both parts`() {
        val (plain, accent) = thanksHeadlineParts("Premium är ditt. *För alltid.*")
        assertEquals("Premium är ditt.", plain)
        assertEquals("För alltid.", accent)
    }

    @Test
    fun `plain text with no accent markup has a null accent`() {
        val (plain, accent) = thanksHeadlineParts("Premium är ditt.")
        assertEquals("Premium är ditt.", plain)
        assertNull(accent)
    }
}
