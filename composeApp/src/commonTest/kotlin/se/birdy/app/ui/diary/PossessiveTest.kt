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
 * Regression for the CRITICAL bug found reviewing T8b/T8c (2026-09-27), refined in T8f:
 * before 1.3.0, [OnboardingViewModel.complete] stored the literal fallback WORD ("Min"/"My" —
 * `onboarding_p3_fallback_name`) as `userName` when the user skipped the name field, instead of
 * leaving it blank. From 1.3.0 it stores "" instead, but already-installed users can still have
 * "Min"/"My" on disk from before. "My" is also a real Swedish given name, so — unlike "Min" — it
 * is masked only when the CURRENT UI language's fallback word IS "My" (English); see
 * [displayNameOrNull]'s KDoc.
 */
class DisplayNameOrNullTest {
    // Mirrors how call sites build maskedNames: HISTORICAL_SV_ONBOARDING_FALLBACK_NAME plus the
    // current UI language's own onboarding_p3_fallback_name value.
    private val svMask = setOf(HISTORICAL_SV_ONBOARDING_FALLBACK_NAME)
    private val enMask = setOf(HISTORICAL_SV_ONBOARDING_FALLBACK_NAME, "My")

    @Test
    fun `Min is masked in the swedish set`() {
        assertEquals(null, displayNameOrNull("Min", svMask))
    }

    @Test
    fun `Min is masked in the english set too`() {
        assertEquals(null, displayNameOrNull("Min", enMask))
    }

    @Test
    fun `My is NOT masked in the swedish set — it is a real swedish name there`() {
        assertEquals("My", displayNameOrNull("My", svMask))
    }

    @Test
    fun `My is masked in the english set — that is where it is the fallback word`() {
        assertEquals(null, displayNameOrNull("My", enMask))
    }

    @Test
    fun `whitespace-only is treated as no name`() {
        assertEquals(null, displayNameOrNull("  ", svMask))
    }

    @Test
    fun `empty is treated as no name`() {
        assertEquals(null, displayNameOrNull("", svMask))
    }

    @Test
    fun `a real name is trimmed and kept`() {
        assertEquals("Albin", displayNameOrNull(" Albin ", svMask))
    }

    @Test
    fun `a name merely starting with a masked word is not mistaken for it`() {
        assertEquals("Mina", displayNameOrNull("Mina", enMask))
        assertEquals("Myra", displayNameOrNull("Myra", enMask))
    }
}
