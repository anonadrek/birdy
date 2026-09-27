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

/**
 * Regression for the CRITICAL bug found reviewing T8b/T8c (2026-09-27): before this fix,
 * [OnboardingViewModel.complete] stored the literal fallback WORD ("Min"/"My" —
 * `onboarding_p3_fallback_name`) as `userName` when the user skipped the name field, instead of
 * leaving it blank. Every reader of `userName` — including existing production users who already
 * have "Min"/"My" persisted — must treat those two values the same as blank. Fixed on every read
 * (this function), not by migrating stored data or changing onboarding's persistence.
 */
class DisplayNameOrNullTest {
    @Test
    fun `the swedish legacy fallback word is treated as no name`() {
        assertEquals(null, displayNameOrNull("Min"))
    }

    @Test
    fun `the english legacy fallback word is treated as no name`() {
        assertEquals(null, displayNameOrNull("My"))
    }

    @Test
    fun `whitespace-only is treated as no name`() {
        assertEquals(null, displayNameOrNull("  "))
    }

    @Test
    fun `empty is treated as no name`() {
        assertEquals(null, displayNameOrNull(""))
    }

    @Test
    fun `a real name is trimmed and kept`() {
        assertEquals("Albin", displayNameOrNull(" Albin "))
    }

    @Test
    fun `a name merely starting with the fallback word is not mistaken for it`() {
        assertEquals("Mina", displayNameOrNull("Mina"))
        assertEquals("Myra", displayNameOrNull("Myra"))
    }
}
