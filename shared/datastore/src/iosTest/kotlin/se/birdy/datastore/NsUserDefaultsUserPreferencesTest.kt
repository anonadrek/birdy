package se.birdy.datastore

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSUserDefaults
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NsUserDefaultsUserPreferencesTest {
    private val suite = "birdy-i1-prefs-test"

    private fun store() = NsUserDefaultsUserPreferences(NSUserDefaults(suiteName = suite)!!)

    @AfterTest
    fun cleanup() {
        NSUserDefaults(suiteName = suite)!!.removePersistentDomainForName(suite)
    }

    @Test
    fun defaults_match_android_when_nothing_persisted() =
        runTest {
            val prefs = store()
            assertEquals("", prefs.userName.first())
            assertEquals(false, prefs.hasSeenOnboarding.first())
            assertEquals(AppLanguage.SYSTEM, prefs.appLanguage.first())
            assertEquals("ALL", prefs.archiveChip.first())
            assertEquals(ArchiveSort.ALPHA, prefs.archiveSort.first())
            assertEquals(true, prefs.dailyBirdPushEnabled.first()) // true-by-default key
            assertEquals(false, prefs.locationCaptureEnabled.first())
            assertNull(prefs.firstInstallTimestamp.first())
        }

    @Test
    fun onboarding_flag_persists_across_instances() =
        runTest {
            store().setHasSeenOnboarding(true)
            // A brand-new instance (simulating an app relaunch) must read the persisted value.
            assertEquals(true, store().hasSeenOnboarding.first())
        }

    @Test
    fun true_default_boolean_survives_being_set_false() =
        runTest {
            store().setDailyBirdPushEnabled(false)
            assertEquals(false, store().dailyBirdPushEnabled.first())
        }

    @Test
    fun nullable_long_round_trips() =
        runTest {
            store().setFirstInstallTimestamp(1_700_000_000_000L)
            assertEquals(1_700_000_000_000L, store().firstInstallTimestamp.first())
        }

    @Test
    fun enum_round_trips() =
        runTest {
            store().setArchiveSort(ArchiveSort.FAMILY)
            assertEquals(ArchiveSort.FAMILY, store().archiveSort.first())
        }

    @Test
    fun string_round_trips() =
        runTest {
            store().setUserName("Albin")
            assertEquals("Albin", store().userName.first())
        }
}
