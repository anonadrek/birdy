package se.birdy.app.ui.premium

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import se.birdy.app.data.premium.FormattedPrices
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

    private val prices = MutableStateFlow(FormattedPrices(yearly = "199 kr", lifetime = "499 kr"))

    @Test
    fun `cancelled purchase does not complete`() =
        runTest {
            val repo = FakePremiumRepository()
            val vm = PremiumViewModel(repo, launchPurchase = { /* user cancels: nothing happens */ }, formattedPricesFlow = prices)
            vm.purchase()
            assertEquals(false, vm.state.first().purchaseCompleted)
        }

    @Test
    fun `purchase completes only when premium becomes active`() =
        runTest {
            val repo = FakePremiumRepository()
            val vm = PremiumViewModel(repo, launchPurchase = { /* async: result arrives later */ }, formattedPricesFlow = prices)
            vm.purchase()
            assertEquals(false, vm.state.first().purchaseCompleted)
            repo.markPurchased(PremiumTier.YEARLY)
            assertEquals(true, vm.state.first().purchaseCompleted)
        }

    @Test
    fun `already active user opening the screen is not treated as a completed purchase`() =
        runTest {
            val repo =
                FakePremiumRepository(
                    PremiumState.Active(
                        PremiumTier.LIFETIME,
                        kotlinx.datetime.Clock.System
                            .now(),
                    ),
                )
            val vm = PremiumViewModel(repo, launchPurchase = {}, formattedPricesFlow = prices)
            assertEquals(false, vm.state.first().purchaseCompleted)
        }
}
