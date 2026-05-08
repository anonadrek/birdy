package se.birdy.app.ui.settings

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import se.birdy.datastore.AppLanguage
import se.birdy.datastore.InMemoryUserPreferences
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @BeforeTest
    fun setMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun resetMain() = Dispatchers.resetMain()

    @Test
    fun `initial state reflects datastore`() =
        runTest {
            val prefs = InMemoryUserPreferences()
            prefs.setUserName("Albin")
            prefs.setAppLanguage(AppLanguage.SV)
            val vm = SettingsViewModel(prefs)
            vm.state.test {
                val s = awaitItem()
                assertEquals("Albin", s.userName)
                assertEquals(AppLanguage.SV, s.language)
            }
        }

    @Test
    fun `saveName updates datastore`() =
        runTest {
            val prefs = InMemoryUserPreferences()
            val vm = SettingsViewModel(prefs)
            vm.saveName("Bjorn")
            prefs.userName.test { assertEquals("Bjorn", awaitItem()) }
        }

    @Test
    fun `saveLanguage updates datastore`() =
        runTest {
            val prefs = InMemoryUserPreferences()
            val vm = SettingsViewModel(prefs)
            vm.saveLanguage(AppLanguage.EN)
            prefs.appLanguage.test { assertEquals(AppLanguage.EN, awaitItem()) }
        }
}
