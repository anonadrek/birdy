package se.birdy.app.data.premium

import kotlinx.coroutines.flow.StateFlow
import se.birdy.domain.premium.PremiumState
import se.birdy.domain.premium.PremiumTier

/**
 * Thin Android Billing v8 wrapper exposed as expect/actual for KMP.
 * - Android actual: wraps com.android.billingclient.api.BillingClient
 * - iOS actual: no-op stub (returns Inactive, throws on launchPurchase)
 *
 * BillingClient lifecycle (connect/disconnect) is handled internally;
 * call `connect()` once at app start and `dispose()` on Activity destroy.
 */
expect class PremiumBillingClient {
    val state: StateFlow<PremiumState>
    val formattedPrices: StateFlow<FormattedPrices>

    /** True once Play has answered a purchase query successfully (never set on a failed query). */
    val purchasesQueried: StateFlow<Boolean>

    suspend fun connect()

    /** @return true once Play has answered; false if it could not be reached. */
    suspend fun queryPurchases(): Boolean

    suspend fun launchPurchase(
        activityContext: Any,
        tier: PremiumTier,
    ): PurchaseResult

    fun dispose()
}

data class FormattedPrices(
    val yearly: String? = null,
    val lifetime: String? = null,
)

sealed interface PurchaseResult {
    data object Success : PurchaseResult

    data object UserCancelled : PurchaseResult

    /**
     * Play accepted the order but payment isn't confirmed yet (e.g. a cash/delayed payment
     * method). Premium turns on later via [PremiumBillingClient.state] (the purchases-updated
     * listener) or the next [PremiumBillingClient.queryPurchases] call once it completes —
     * nothing more to do here than tell the user to expect it.
     */
    data object Pending : PurchaseResult

    data class Error(
        val message: String,
    ) : PurchaseResult
}
