package se.birdy.app.ui.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import se.birdy.app.testing.FakePremiumRepository
import se.birdy.datastore.InMemoryUserPreferences
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsLocationToggleTest {
    @BeforeTest
    fun setMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun resetMain() = Dispatchers.resetMain()

    @Test
    fun togglePersistsToPrefs() =
        runTest {
            val prefs = InMemoryUserPreferences()
            val vm = SettingsViewModel(prefs, FakePremiumRepository())
            vm.setLocationCaptureEnabled(true)
            assertEquals(true, prefs.locationCaptureEnabled.first())
        }
}
