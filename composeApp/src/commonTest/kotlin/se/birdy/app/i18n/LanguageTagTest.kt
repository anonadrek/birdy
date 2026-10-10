package se.birdy.app.i18n

import se.birdy.datastore.AppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals

class LanguageTagTest {
    @Test
    fun `an empty per-app locale list means follow the system`() {
        assertEquals(AppLanguage.SYSTEM, appLanguageFromLocaleTags(""))
        assertEquals(AppLanguage.SYSTEM, appLanguageFromLocaleTags("  "))
    }

    @Test
    fun `the first tag decides and regions do not matter`() {
        assertEquals(AppLanguage.EN, appLanguageFromLocaleTags("en"))
        assertEquals(AppLanguage.EN, appLanguageFromLocaleTags("en-GB"))
        assertEquals(AppLanguage.SV, appLanguageFromLocaleTags("sv-SE"))
        assertEquals(AppLanguage.SV, appLanguageFromLocaleTags("sv-SE,en-US"))
        assertEquals(AppLanguage.EN, appLanguageFromLocaleTags("EN-us"))
    }

    @Test
    fun `a language the app does not have follows the system`() {
        assertEquals(AppLanguage.SYSTEM, appLanguageFromLocaleTags("de-DE"))
    }

    @Test
    fun `the round trip through the tag keeps every choice`() {
        for (language in AppLanguage.entries) {
            assertEquals(language, appLanguageFromLocaleTags(language.toLocaleTagOrEmpty()))
        }
    }
}
