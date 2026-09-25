package se.birdy.app.data.premium

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import se.birdy.domain.premium.PremiumState
import se.birdy.domain.premium.PremiumTier

class FakePremiumBillingClient {
    private val _state = MutableStateFlow<PremiumState>(PremiumState.Free)
    val state: StateFlow<PremiumState> = _state.asStateFlow()
    val formattedPrices = MutableStateFlow(FormattedPrices("199 SEK / year", "499 SEK"))

    // Call counter, not to be confused with the real PremiumBillingClient.purchasesQueried
    // StateFlow<Boolean> (whether Play has answered).
    var queryPurchasesCalls = 0
    var purchaseLaunched: PremiumTier? = null
    var nextPurchaseResult: PurchaseResult = PurchaseResult.Success
    var disposed = false

    fun setActive(tier: PremiumTier) {
        _state.value =
            PremiumState.Active(
                tier,
                kotlinx.datetime.Clock.System
                    .now(),
            )
    }

    fun setFree() {
        _state.value = PremiumState.Free
    }

    /** Builds a `BillingPremiumRepository`-shaped lambda that counts calls and returns [result]. */
    fun queryPurchases(result: Boolean): suspend () -> Boolean =
        {
            queryPurchasesCalls++
            result
        }

    // mimics actual PremiumBillingClient surface for repository wiring
}
