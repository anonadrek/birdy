package se.birdy.app.ui.onboarding

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import se.birdy.datastore.InMemoryUserPreferences
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    @BeforeTest
    fun setMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun resetMain() = Dispatchers.resetMain()

    @Test
    fun `initial state is page 0 with empty name`() =
        runTest {
            val prefs = InMemoryUserPreferences()
            val vm = OnboardingViewModel(prefs, defaultFallbackName = "Min")
            vm.state.test {
                val first = awaitItem()
                assertTrue(first is OnboardingUiState.Visible)
                assertEquals(0, first.pageIndex)
                assertEquals("", first.nameInput)
            }
        }

    @Test
    fun `setPageIndex moves between pages`() =
        runTest {
            val vm = OnboardingViewModel(InMemoryUserPreferences(), "Min")
            vm.setPageIndex(1)
            vm.setPageIndex(2)
            vm.state.test {
                val s = awaitItem()
                assertTrue(s is OnboardingUiState.Visible)
                assertEquals(2, s.pageIndex)
            }
        }

    @Test
    fun `complete with empty name uses fallback`() =
        runTest {
            val prefs = InMemoryUserPreferences()
            val vm = OnboardingViewModel(prefs, defaultFallbackName = "Min")
            vm.onNameChange("")
            vm.complete()
            prefs.userName.test { assertEquals("Min", awaitItem()) }
            prefs.hasSeenOnboarding.test { assertEquals(true, awaitItem()) }
        }

    @Test
    fun `complete with non-empty name stores it`() =
        runTest {
            val prefs = InMemoryUserPreferences()
            val vm = OnboardingViewModel(prefs, defaultFallbackName = "Min")
            vm.onNameChange("Albin")
            vm.complete()
            prefs.userName.test { assertEquals("Albin", awaitItem()) }
        }

    @Test
    fun `complete trims leading and trailing whitespace`() =
        runTest {
            val prefs = InMemoryUserPreferences()
            val vm = OnboardingViewModel(prefs, defaultFallbackName = "Min")
            vm.onNameChange("  Albin  ")
            vm.complete()
            prefs.userName.test { assertEquals("Albin", awaitItem()) }
        }
}
