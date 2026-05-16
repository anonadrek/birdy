package se.birdy.app.ui.premium

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import se.birdy.app.testing.FakePremiumRepository
import se.birdy.domain.premium.PremiumState
import se.birdy.domain.premium.PremiumTier
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class PremiumViewModelTest {
    @BeforeTest
    fun setMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun resetMain() = Dispatchers.resetMain()

    @Test
    fun `initial state is YEARLY selected`() =
        runTest {
            val repo = FakePremiumRepository()
            val vm = PremiumViewModel(repo)
            assertEquals(PremiumTier.YEARLY, vm.state.first().selectedTier)
        }

    @Test
    fun `selectTier LIFETIME updates state`() =
        runTest {
            val repo = FakePremiumRepository()
            val vm = PremiumViewModel(repo)
            vm.selectTier(PremiumTier.LIFETIME)
            assertEquals(PremiumTier.LIFETIME, vm.state.first().selectedTier)
        }

    @Test
    fun `purchase emits Active state via explicit launchPurchase stub`() =
        runTest {
            val repo = FakePremiumRepository()
            // Must provide an explicit stub — default throws to prevent silent no-ops in tests.
            val vm = PremiumViewModel(repo, launchPurchase = { tier -> repo.markPurchased(tier) })
            vm.selectTier(PremiumTier.LIFETIME)
            vm.purchase()
            val s = repo.state.first()
            assertIs<PremiumState.Active>(s)
            assertEquals(PremiumTier.LIFETIME, s.tier)
        }
}
