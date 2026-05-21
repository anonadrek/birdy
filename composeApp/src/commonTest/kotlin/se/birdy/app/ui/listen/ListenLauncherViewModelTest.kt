package se.birdy.app.ui.listen

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import se.birdy.domain.premium.PremiumState
import se.birdy.domain.premium.PremiumTier
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
    fun `audioCard_active_emitsNavigateToAudioScan`() =
        runTest(dispatcher) {
            val flow =
                MutableStateFlow<PremiumState>(
                    PremiumState.Active(PremiumTier.YEARLY, Instant.fromEpochMilliseconds(0)),
                )
            val vm = ListenLauncherViewModel(flow)
            vm.effects.test {
                vm.onAudioCardTap()
                assertIs<ListenLauncherEffect.NavigateToAudioScan>(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `audioCard_free_emitsNavigateToPremium`() =
        runTest(dispatcher) {
            val flow = MutableStateFlow<PremiumState>(PremiumState.Free)
            val vm = ListenLauncherViewModel(flow)
            vm.effects.test {
                vm.onAudioCardTap()
                assertIs<ListenLauncherEffect.NavigateToPremium>(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `audioCard_stateChange_routesPerLatestState`() =
        runTest(dispatcher) {
            val flow = MutableStateFlow<PremiumState>(PremiumState.Free)
            val vm = ListenLauncherViewModel(flow)

            vm.effects.test {
                // First tap as Free → NavigateToPremium
                vm.onAudioCardTap()
                assertIs<ListenLauncherEffect.NavigateToPremium>(awaitItem())

                // Flip to Active
                flow.value = PremiumState.Active(PremiumTier.LIFETIME, Instant.fromEpochMilliseconds(0))

                // Second tap as Active → NavigateToAudioScan
                vm.onAudioCardTap()
                assertIs<ListenLauncherEffect.NavigateToAudioScan>(awaitItem())

                cancelAndIgnoreRemainingEvents()
            }
        }
}
