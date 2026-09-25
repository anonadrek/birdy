package se.birdy.app.ui.premium

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import se.birdy.app.data.premium.FormattedPrices
import se.birdy.app.data.premium.PurchaseResult
import se.birdy.app.testing.FakePremiumRepository
import se.birdy.domain.premium.PremiumState
import se.birdy.domain.premium.PremiumTier
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

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
            val vm =
                PremiumViewModel(
                    repo,
                    launchPurchase = { tier ->
                        repo.markPurchased(tier)
                        PurchaseResult.Success
                    },
                )
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
            val vm =
                PremiumViewModel(
                    repo,
                    launchPurchase = { PurchaseResult.UserCancelled },
                    formattedPricesFlow = prices,
                )
            vm.purchase()
            assertEquals(false, vm.state.first().purchaseCompleted)
        }

    @Test
    fun `purchase completes only when premium becomes active`() =
        runTest {
            val repo = FakePremiumRepository()
            // async: the result (Pending) arrives immediately, but activation itself arrives
            // later via the repository's state flow — completion must wait for that, not for
            // launchPurchase's return.
            val vm =
                PremiumViewModel(
                    repo,
                    launchPurchase = { PurchaseResult.Pending },
                    formattedPricesFlow = prices,
                )
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
                        Clock.System.now(),
                    ),
                )
            val vm = PremiumViewModel(repo, launchPurchase = { PurchaseResult.Success }, formattedPricesFlow = prices)
            assertEquals(false, vm.state.first().purchaseCompleted)
        }

    @Test
    fun `pending purchase shows the pending notice`() =
        runTest {
            val repo = FakePremiumRepository()
            val vm =
                PremiumViewModel(
                    repo,
                    launchPurchase = { PurchaseResult.Pending },
                    formattedPricesFlow = prices,
                )
            vm.purchase()
            val s = vm.state.first()
            assertEquals(PurchaseNotice.PENDING, s.purchaseNotice)
            assertEquals(false, s.purchaseCompleted)
        }

    @Test
    fun `failed purchase shows the failed notice`() =
        runTest {
            val repo = FakePremiumRepository()
            val vm =
                PremiumViewModel(
                    repo,
                    launchPurchase = { PurchaseResult.Error("x") },
                    formattedPricesFlow = prices,
                )
            vm.purchase()
            assertEquals(PurchaseNotice.FAILED, vm.state.first().purchaseNotice)
        }

    @Test
    fun `cancelled purchase shows no notice`() =
        runTest {
            val repo = FakePremiumRepository()
            val vm =
                PremiumViewModel(
                    repo,
                    launchPurchase = { PurchaseResult.UserCancelled },
                    formattedPricesFlow = prices,
                )
            vm.purchase()
            assertNull(vm.state.first().purchaseNotice)
        }

    @Test
    fun `a throwing launchPurchase shows the failed notice`() =
        runTest {
            val repo = FakePremiumRepository()
            val vm =
                PremiumViewModel(
                    repo,
                    launchPurchase = { error("boom") },
                    formattedPricesFlow = prices,
                )
            vm.purchase()
            val s = vm.state.first()
            assertEquals(PurchaseNotice.FAILED, s.purchaseNotice)
            assertEquals(false, s.purchaseInFlight)
        }

    @Test
    fun `a new purchase attempt clears the previous notice`() =
        runTest {
            var result: PurchaseResult = PurchaseResult.Error("first")
            val repo = FakePremiumRepository()
            val vm =
                PremiumViewModel(
                    repo,
                    launchPurchase = { result },
                    formattedPricesFlow = prices,
                )
            vm.purchase()
            assertEquals(PurchaseNotice.FAILED, vm.state.first().purchaseNotice)

            result = PurchaseResult.UserCancelled
            vm.purchase()
            assertNull(vm.state.first().purchaseNotice)
        }

    @Test
    fun `cancel then an entitlement arriving later completes the purchase`() =
        runTest {
            val repo = FakePremiumRepository()
            val vm =
                PremiumViewModel(
                    repo,
                    launchPurchase = { PurchaseResult.UserCancelled },
                    formattedPricesFlow = prices,
                )
            vm.purchase()
            assertEquals(false, vm.state.first().purchaseCompleted)

            // awaitingActivation stays sticky after a cancel — a real entitlement arriving later
            // (e.g. a pending purchase from another attempt completing) still finishes the flow.
            repo.markPurchased(PremiumTier.LIFETIME)
            assertEquals(true, vm.state.first().purchaseCompleted)
        }
}
