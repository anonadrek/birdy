package se.birdy.app.util

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regression coverage for T10b's bug fix: the badge rule's old local `RED_LISTED` set was
 * missing `"EN"` (Endangered) — see [isRedListed]'s KDoc.
 */
class RedListStatusTest {
    @Test
    fun `NT VU EN and CR count as red-listed`() {
        assertTrue(isRedListed("NT"))
        assertTrue(isRedListed("VU"))
        assertTrue(isRedListed("EN"))
        assertTrue(isRedListed("CR"))
    }

    @Test
    fun `LC DD NE blank and null do not count as red-listed`() {
        assertFalse(isRedListed("LC"))
        assertFalse(isRedListed("DD"))
        assertFalse(isRedListed("NE"))
        assertFalse(isRedListed(""))
        assertFalse(isRedListed(null))
    }
}
