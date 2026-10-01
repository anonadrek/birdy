package se.birdy.datastore

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GrandfatherPrefsTest {
    @Test
    fun `thanks shown defaults to false and persists true`() =
        runTest {
            val prefs = InMemoryUserPreferences()
            assertFalse(prefs.grandfatherThanksShown.first())
            prefs.setGrandfatherThanksShown(true)
            assertTrue(prefs.grandfatherThanksShown.first())
        }

    @Test
    fun `debug force grandfathered defaults to false and can be toggled`() =
        runTest {
            val prefs = InMemoryUserPreferences()
            assertFalse(prefs.debugForceGrandfathered.first())
            prefs.setDebugForceGrandfathered(true)
            assertTrue(prefs.debugForceGrandfathered.first())
        }

    @Test
    fun `early-user evidence is empty by default`() =
        runTest {
            val prefs = InMemoryUserPreferences()
            assertFalse(prefs.grandfatherLegacyCaptured.first())
            assertNull(prefs.grandfatherLegacyInstallMs.first())
            assertNull(prefs.grandfatherTrustedFirstSeenMs.first())
        }

    @Test
    fun `capturing legacy evidence stores the value and marks the capture done`() =
        runTest {
            val prefs = InMemoryUserPreferences()
            prefs.captureGrandfatherLegacy(1_780_000_000_000L)
            assertTrue(prefs.grandfatherLegacyCaptured.first())
            assertEquals(1_780_000_000_000L, prefs.grandfatherLegacyInstallMs.first())
        }

    @Test
    fun `capturing without a legacy value still marks the capture done`() =
        runTest {
            val prefs = InMemoryUserPreferences()
            prefs.captureGrandfatherLegacy(null)
            assertTrue(prefs.grandfatherLegacyCaptured.first())
            assertNull(prefs.grandfatherLegacyInstallMs.first())
        }

    @Test
    fun `trusted first seen time round trips`() =
        runTest {
            val prefs = InMemoryUserPreferences()
            prefs.setGrandfatherTrustedFirstSeenMs(1_790_000_000_000L)
            assertEquals(1_790_000_000_000L, prefs.grandfatherTrustedFirstSeenMs.first())
        }
}
