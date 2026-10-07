package se.birdy.app.i18n

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import se.birdy.datastore.AppLanguage
import se.birdy.datastore.InMemoryUserPreferences
import se.birdy.datastore.UserPreferences
import kotlin.test.Test
import kotlin.test.assertEquals

class AppLanguageSyncTest {
    private class CountingPreferences(
        val inner: InMemoryUserPreferences = InMemoryUserPreferences(),
    ) : UserPreferences by inner {
        var languageWrites = 0

        override suspend fun setAppLanguage(value: AppLanguage) {
            languageWrites += 1
            inner.setAppLanguage(value)
        }
    }

    private suspend fun prefsWith(language: AppLanguage) =
        CountingPreferences().apply {
            inner.setAppLanguage(language)
        }

    @Test
    fun `without a per-app language the preference stays and nothing is written`() =
        runTest {
            val prefs = prefsWith(AppLanguage.SV)
            assertEquals(AppLanguage.SV, reconcileAppLanguage(prefs, appliedTags = null))
            assertEquals(0, prefs.languageWrites)
        }

    @Test
    fun `an empty per-app list means system and is written back`() =
        runTest {
            val prefs = prefsWith(AppLanguage.SV)
            assertEquals(AppLanguage.SYSTEM, reconcileAppLanguage(prefs, appliedTags = ""))
            assertEquals(1, prefs.languageWrites)
            assertEquals(AppLanguage.SYSTEM, prefs.inner.appLanguage.first())
        }

    @Test
    fun `a language chosen in Android settings wins and is written back`() =
        runTest {
            val prefs = prefsWith(AppLanguage.SV)
            assertEquals(AppLanguage.EN, reconcileAppLanguage(prefs, appliedTags = "en-US"))
            assertEquals(AppLanguage.EN, prefs.inner.appLanguage.first())
        }

    @Test
    fun `when both agree nothing is written`() =
        runTest {
            val prefs = prefsWith(AppLanguage.EN)
            assertEquals(AppLanguage.EN, reconcileAppLanguage(prefs, appliedTags = "en"))
            assertEquals(0, prefs.languageWrites)
        }
}
