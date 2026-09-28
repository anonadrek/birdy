package se.birdy.app.ui.onboarding

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
            val vm = OnboardingViewModel(prefs)
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
            val vm = OnboardingViewModel(InMemoryUserPreferences())
            vm.setPageIndex(1)
            vm.setPageIndex(2)
            vm.state.test {
                val s = awaitItem()
                assertTrue(s is OnboardingUiState.Visible)
                assertEquals(2, s.pageIndex)
            }
        }

    // T8f: skipping the name field stores "" now, not a placeholder fallback word — masking a
    // stored "Min"/"My" for display (see Possessive.kt's displayNameOrNull) is what handles the
    // already-installed users who have the old fallback word on disk from before this change.
    @Test
    fun `complete with empty name stores empty - not a fallback word`() =
        runTest {
            val prefs = InMemoryUserPreferences()
            val vm = OnboardingViewModel(prefs)
            vm.onNameChange("")
            vm.complete()
            prefs.userName.test { assertEquals("", awaitItem()) }
            prefs.hasSeenOnboarding.test { assertEquals(true, awaitItem()) }
        }

    @Test
    fun `complete with non-empty name stores it`() =
        runTest {
            val prefs = InMemoryUserPreferences()
            val vm = OnboardingViewModel(prefs)
            vm.onNameChange("Albin")
            vm.complete()
            prefs.userName.test { assertEquals("Albin", awaitItem()) }
        }

    @Test
    fun `complete trims leading and trailing whitespace`() =
        runTest {
            val prefs = InMemoryUserPreferences()
            val vm = OnboardingViewModel(prefs)
            vm.onNameChange("  Albin  ")
            vm.complete()
            prefs.userName.test { assertEquals("Albin", awaitItem()) }
        }

    @Test
    fun `setPageIndex moves to page 7`() =
        runTest {
            val vm = OnboardingViewModel(InMemoryUserPreferences())
            vm.setPageIndex(7)
            vm.state.test {
                val s = awaitItem()
                assertTrue(s is OnboardingUiState.Visible)
                assertEquals(7, s.pageIndex)
            }
        }

    @Test
    fun `setPageIndex coerces 8 to 7 MAX_PAGE_INDEX`() =
        runTest {
            val vm = OnboardingViewModel(InMemoryUserPreferences())
            vm.setPageIndex(8)
            vm.state.test {
                val s = awaitItem()
                assertTrue(s is OnboardingUiState.Visible)
                assertEquals(7, s.pageIndex)
            }
        }

    @Test
    fun `selectLanguage persists immediately and applies locale`() =
        runTest {
            val prefs = InMemoryUserPreferences()
            val applied = mutableListOf<String>()
            val vm = OnboardingViewModel(prefs, applyLocaleFn = { applied += it })
            vm.selectLanguage(AppLanguage.EN)
            prefs.appLanguage.test { assertEquals(AppLanguage.EN, awaitItem()) }
            assertEquals(listOf("en"), applied)
            val s = vm.state.value
            assertTrue(s is OnboardingUiState.Visible)
            assertEquals(AppLanguage.EN, s.selectedLanguage)
        }

    @Test
    fun `selectLanguage persists in replay mode too`() =
        runTest {
            val prefs = InMemoryUserPreferences()
            val vm = OnboardingViewModel(prefs, isReplay = true, applyLocaleFn = {})
            vm.selectLanguage(AppLanguage.SV)
            prefs.appLanguage.test { assertEquals(AppLanguage.SV, awaitItem()) }
        }

    @Test
    fun `init reads stored non-system language into state`() =
        runTest {
            val prefs = InMemoryUserPreferences()
            prefs.setAppLanguage(AppLanguage.EN)
            val vm = OnboardingViewModel(prefs, applyLocaleFn = {})
            vm.state.test {
                val s = awaitItem()
                assertTrue(s is OnboardingUiState.Visible)
                assertEquals(AppLanguage.EN, s.selectedLanguage)
            }
        }

    @Test
    fun `replay mode does not write hasSeenOnboarding on complete`() =
        runTest {
            val prefs = InMemoryUserPreferences()
            // simulate user has already seen onboarding before replay
            prefs.setHasSeenOnboarding(true)
            prefs.setUserName("Albin")
            val vm = OnboardingViewModel(prefs, isReplay = true)
            vm.onNameChange("Ignored")
            vm.complete()
            prefs.userName.test { assertEquals("Albin", awaitItem()) } // unchanged
            prefs.hasSeenOnboarding.test { assertEquals(true, awaitItem()) }
        }

    @Test
    fun `replay mode still transitions state to Done`() =
        runTest {
            val vm = OnboardingViewModel(InMemoryUserPreferences(), isReplay = true)
            vm.complete()
            vm.state.test {
                assertEquals(OnboardingUiState.Done, awaitItem())
            }
        }
}
