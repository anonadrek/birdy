package se.birdy.app.i18n

import se.birdy.content.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class LocaleResolverTest {
    @Test
    fun override_wins_over_system() {
        val result = LocaleResolver.resolve(override = "en", systemTag = "sv-SE")
        assertEquals(Locale.EN, result)
    }

    @Test
    fun system_locale_used_when_no_override() {
        assertEquals(Locale.SV, LocaleResolver.resolve(override = null, systemTag = "sv-SE"))
        assertEquals(Locale.EN, LocaleResolver.resolve(override = null, systemTag = "en-US"))
    }

    @Test
    fun fallback_to_sv_for_unknown_system_locale() {
        assertEquals(Locale.SV, LocaleResolver.resolve(override = null, systemTag = "de-DE"))
    }

    @Test
    fun override_with_subtag_normalizes() {
        assertEquals(Locale.EN, LocaleResolver.resolve(override = "en-GB", systemTag = "sv-SE"))
    }

    @Test
    fun system_tag_case_insensitive() {
        assertEquals(Locale.EN, LocaleResolver.resolve(override = null, systemTag = "EN-US"))
    }

    @Test
    fun empty_system_tag_falls_back_to_sv() {
        assertEquals(Locale.SV, LocaleResolver.resolve(override = null, systemTag = ""))
    }
}
