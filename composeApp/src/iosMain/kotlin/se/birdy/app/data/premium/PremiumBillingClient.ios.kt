package se.birdy.app.data.premium

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import se.birdy.domain.premium.PremiumState
import se.birdy.domain.premium.PremiumTier

/** iOS stub. Real StoreKit 2 implementation lands in plan i5. */
actual class PremiumBillingClient {
    actual val state: StateFlow<PremiumState> = MutableStateFlow(PremiumState.Free)
    actual val formattedPrices: StateFlow<FormattedPrices> = MutableStateFlow(FormattedPrices())

    // iOS has no automatic paywall before the StoreKit work (plan i5) — true keeps today's
    // behavior (the caller never blocks waiting for an answer that will never come).
    actual val purchasesQueried: StateFlow<Boolean> = MutableStateFlow(true)

    actual suspend fun connect() = Unit

    // No real billing client to be unreachable yet (plan i5) — mirrors the trivial no-op style
    // of the other stubs on this class rather than modeling a failure that can't happen here.
    actual suspend fun queryPurchases(): Boolean = true

    actual suspend fun launchPurchase(
        activityContext: Any,
        tier: PremiumTier,
    ): PurchaseResult = PurchaseResult.Error("Purchases land on iOS in plan i5 (StoreKit)")

    actual fun dispose() = Unit
}
