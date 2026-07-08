package se.birdy.app.data.premium

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import se.birdy.domain.premium.PremiumState
import se.birdy.domain.premium.PremiumTier

/** iOS stub. Real StoreKit 2 implementation lands in plan i5. */
actual class PremiumBillingClient {
    actual val state: StateFlow<PremiumState> = MutableStateFlow(PremiumState.Free)
    actual val formattedPrices: StateFlow<FormattedPrices> = MutableStateFlow(FormattedPrices())

    actual suspend fun connect() = Unit

    actual suspend fun queryPurchases() = Unit

    actual suspend fun launchPurchase(
        activityContext: Any,
        tier: PremiumTier,
    ): PurchaseResult = PurchaseResult.Error("Purchases land on iOS in plan i5 (StoreKit)")

    actual fun dispose() = Unit
}
