package se.birdy.datastore

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class LocationCapturePrefTest {
    @Test
    fun defaultsFalseThenTogglesTrue() =
        runTest {
            val prefs = InMemoryUserPreferences()
            assertEquals(false, prefs.locationCaptureEnabled.first())
            prefs.setLocationCaptureEnabled(true)
            assertEquals(true, prefs.locationCaptureEnabled.first())
        }
}
