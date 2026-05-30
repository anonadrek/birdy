package se.birdy.app.ui.recap

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import se.birdy.app.testing.FakeBadgeRepository
import se.birdy.app.testing.FakeObservationRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RecapViewModelTest {
    @BeforeTest fun setMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun resetMain() = Dispatchers.resetMain()

    private fun vm(
        obs: FakeObservationRepository,
        badges: FakeBadgeRepository,
    ) = RecapViewModel(
        obsRepo = obs,
        badgeRepo = badges,
        speciesByQid = { emptyMap() },
        badgeNameFor = { it },
        zone = TimeZone.UTC,
        now = { Clock.System.now() },
    )

    @Test
    fun `quiet week emits Loaded with quiet summary`() =
        runTest {
            val vm = vm(FakeObservationRepository(), FakeBadgeRepository())
            vm.state.test {
                var item = awaitItem()
                while (item is RecapUiState.Loading) item = awaitItem()
                val loaded = item as RecapUiState.Loaded
                assertTrue(loaded.recap.summary.isQuiet)
            }
        }
}
