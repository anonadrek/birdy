package se.birdy.datastore

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
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
}
