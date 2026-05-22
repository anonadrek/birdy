package se.birdy.app.ui.listen

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class ListenLauncherViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest fun setMain() = Dispatchers.setMain(dispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `audioCardTap_emitsNavigateToAudioScan`() =
        runTest(dispatcher) {
            val vm = ListenLauncherViewModel()
            vm.effects.test {
                vm.onAudioCardTap()
                assertIs<ListenLauncherEffect.NavigateToAudioScan>(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
}
