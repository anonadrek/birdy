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

    suspend fun connect()

    suspend fun queryPurchases()

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

    data class Error(
        val message: String,
    ) : PurchaseResult
}
