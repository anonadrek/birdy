package se.birdy.datastore

import app.cash.turbine.test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class InMemoryUserPreferencesTest {
    @Test
    fun `userName starts empty and updates`() =
        runTest {
            val prefs = InMemoryUserPreferences()
            prefs.userName.test {
                assertEquals("", awaitItem())
                prefs.setUserName("Albin")
                assertEquals("Albin", awaitItem())
            }
        }

    @Test
    fun `hasSeenOnboarding starts false`() =
        runTest {
            val prefs = InMemoryUserPreferences()
            prefs.hasSeenOnboarding.test {
                assertEquals(false, awaitItem())
                prefs.setHasSeenOnboarding(true)
                assertEquals(true, awaitItem())
            }
        }

    @Test
    fun `appLanguage default is SYSTEM`() =
        runTest {
            val prefs = InMemoryUserPreferences()
            prefs.appLanguage.test {
                assertEquals(AppLanguage.SYSTEM, awaitItem())
                prefs.setAppLanguage(AppLanguage.SV)
                assertEquals(AppLanguage.SV, awaitItem())
            }
        }

    @Test
    fun `all enum-backed prefs round-trip`() =
        runTest {
            val prefs = InMemoryUserPreferences()
            prefs.setLifelistStat3(LifelistStat3Choice.LONGEST_STREAK)
            prefs.setArchiveSort(ArchiveSort.RECENT)
            prefs.setLifelistSort(LifelistSort.STAMP_NUMBER)

            assertEquals(LifelistStat3Choice.LONGEST_STREAK, prefs.lifelistStat3.first())
            assertEquals(ArchiveSort.RECENT, prefs.archiveSort.first())
            assertEquals(LifelistSort.STAMP_NUMBER, prefs.lifelistSort.first())
        }
}
