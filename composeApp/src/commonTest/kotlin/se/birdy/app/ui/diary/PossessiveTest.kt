package se.birdy.app.ui.diary

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Bug fix (since commit 13253880): the Lifelist headline interpolated the raw user name
 * ("Albin dagbok.") instead of a genitive form ("Albins dagbok."). [possessive] is the pure,
 * locale-agnostic core — Swedish and English each pass in their own suffix/sibilant rules.
 */
class PossessiveTest {
    // Swedish: suffix "s", sibilant suffix "" (nothing added), sibilant endings "sxz".
    @Test
    fun `swedish name not ending in a sibilant gets an s`() {
        assertEquals("Albins", possessive("Albin", suffix = "s", sibilantSuffix = "", sibilantEndings = "sxz"))
    }

    @Test
    fun `swedish name ending in s gets no extra suffix`() {
        assertEquals("Lars", possessive("Lars", suffix = "s", sibilantSuffix = "", sibilantEndings = "sxz"))
    }

    @Test
    fun `swedish name ending in x gets no extra suffix`() {
        assertEquals("Max", possessive("Max", suffix = "s", sibilantSuffix = "", sibilantEndings = "sxz"))
    }

    @Test
    fun `swedish name ending in z gets no extra suffix`() {
        assertEquals("Liz", possessive("Liz", suffix = "s", sibilantSuffix = "", sibilantEndings = "sxz"))
    }

    @Test
    fun `swedish name ending in a vowel gets an s`() {
        assertEquals("Annas", possessive("Anna", suffix = "s", sibilantSuffix = "", sibilantEndings = "sxz"))
    }

    @Test
    fun `swedish trims trailing whitespace before appending`() {
        assertEquals("Albins", possessive("Albin  ", suffix = "s", sibilantSuffix = "", sibilantEndings = "sxz"))
    }

    // English: suffix "’s", sibilant suffix "’", sibilant endings "s".
    @Test
    fun `english name not ending in a sibilant gets apostrophe-s`() {
        assertEquals("Albin’s", possessive("Albin", suffix = "’s", sibilantSuffix = "’", sibilantEndings = "s"))
    }

    @Test
    fun `english name ending in s gets only an apostrophe`() {
        assertEquals("James’", possessive("James", suffix = "’s", sibilantSuffix = "’", sibilantEndings = "s"))
    }

    @Test
    fun `english name ending in x still gets apostrophe-s`() {
        assertEquals("Max’s", possessive("Max", suffix = "’s", sibilantSuffix = "’", sibilantEndings = "s"))
    }
}
