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

    // Release 1.3.0 Task 7g: Garfågel, Kanariestrandskata and Smalnäbbad spov are extinct (EX);
    // they get their own tag instead of passing for red-listed or for not evaluated.
    @Test
    fun `EX and EW are extinct and not red-listed`() {
        assertTrue(isExtinct("EX"))
        assertTrue(isExtinct("EW"))
        assertFalse(isRedListed("EX"))
        assertFalse(isRedListed("EW"))
    }

    @Test
    fun `living statuses blank and null are not extinct`() {
        listOf("LC", "NT", "VU", "EN", "CR", "DD", "NE", "").forEach { assertFalse(isExtinct(it), it) }
        assertFalse(isExtinct(null))
    }
}
