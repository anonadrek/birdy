package se.birdy.app.data.premium

import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private const val TAG = "PremiumBilling"

/** One purchase's ack-relevant fields, stripped of every Billing SDK type. */
internal data class AckCandidate(
    val purchaseToken: String,
    val purchaseState: Int,
    val signatureOk: Boolean,
    val isAcknowledged: Boolean,
)

/**
 * Pure decision, extracted from [PremiumBillingClient.queryPurchases] for unit testability
 * without the Billing SDK: which purchase tokens need acknowledging. A purchase needs it
 * exactly when it is PURCHASED, its signature verified, and Play doesn't already consider it
 * acknowledged (never double-acknowledge).
 */
internal fun purchasesNeedingAcknowledgement(candidates: List<AckCandidate>): Set<String> =
    candidates
        .asSequence()
        .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED && it.signatureOk && !it.isAcknowledged }
        .map { it.purchaseToken }
        .toSet()

/**
 * Acknowledges [purchase] and logs a warning on failure — used by both the listener path
 * (fire-and-forget, launched on the client's own [kotlinx.coroutines.CoroutineScope]) and
 * [PremiumBillingClient.queryPurchases] (same fire-and-forget style). Always acknowledges;
 * callers are responsible for only calling this when [purchasesNeedingAcknowledgement] says so.
 */
internal suspend fun acknowledgeAndLog(
    client: BillingClient,
    purchase: Purchase,
) {
    val result =
        suspendCancellableCoroutine<BillingResult> { cont ->
            val params =
                AcknowledgePurchaseParams
                    .newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
            client.acknowledgePurchase(params) { r -> if (cont.isActive) cont.resume(r) }
        }
    if (result.responseCode != BillingClient.BillingResponseCode.OK) {
        Log.w(TAG, "acknowledge failed for ${purchase.orderId}: ${result.debugMessage}")
    }
}
