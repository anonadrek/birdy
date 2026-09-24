package se.birdy.app.data.premium

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryProductDetailsResult
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.datetime.Clock
import se.birdy.app.BuildConfig
import se.birdy.domain.premium.PremiumState
import se.birdy.domain.premium.PremiumTier
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import kotlin.coroutines.resume
import java.util.Base64 as JvmBase64

private const val TAG = "PremiumBilling"
private const val YEARLY_PRODUCT_ID = "premium_yearly_v1"
private const val LIFETIME_PRODUCT_ID = "premium_lifetime_v1"

/**
 * Pure-JVM signature verification — extracted for unit testability.
 *
 * No Android dependencies (uses [java.util.Base64], no android.util.Log) so
 * this function is exercisable in JVM unit tests without Robolectric.
 *
 * Only reachable in DEBUG with a blank [licensePublicKeyBase64];
 * [PremiumBillingClient.init] enforces the key is present in release builds.
 */
@SuppressLint("NewApi")
internal fun verifyPlaySignature(
    originalJson: String,
    signatureBase64: String,
    licensePublicKeyBase64: String,
): Boolean {
    if (signatureBase64.isEmpty() || originalJson.isEmpty()) return false
    if (licensePublicKeyBase64.isBlank()) {
        // Only reachable in DEBUG builds (PremiumBillingClient.init enforces this).
        return true
    }
    return try {
        val keyBytes = JvmBase64.getDecoder().decode(licensePublicKeyBase64)
        val publicKey: PublicKey =
            KeyFactory
                .getInstance("RSA")
                .generatePublic(X509EncodedKeySpec(keyBytes))
        val sig = Signature.getInstance("SHA1withRSA")
        sig.initVerify(publicKey)
        sig.update(originalJson.toByteArray())
        val sigBytes = JvmBase64.getDecoder().decode(signatureBase64)
        sig.verify(sigBytes)
    } catch (t: Throwable) {
        false
    }
}

actual class PremiumBillingClient(
    private val context: Context,
    private val licensePublicKeyBase64: String,
) {
    init {
        check(BuildConfig.DEBUG || licensePublicKeyBase64.isNotBlank()) {
            "PLAY_LICENSE_KEY missing in release build — billing cannot operate. " +
                "Set BIRDY_PLAY_LICENSE_KEY in gradle.properties."
        }
    }

    private val _state = MutableStateFlow<PremiumState>(PremiumState.Free)
    actual val state: StateFlow<PremiumState> = _state.asStateFlow()

    private val _formattedPrices = MutableStateFlow(FormattedPrices())
    actual val formattedPrices: StateFlow<FormattedPrices> = _formattedPrices.asStateFlow()

    private val _purchasesQueried = MutableStateFlow(false)
    actual val purchasesQueried: StateFlow<Boolean> = _purchasesQueried.asStateFlow()

    private var purchaseDeferred: CompletableDeferred<PurchaseResult>? = null
    private var yearlyDetails: ProductDetails? = null
    private var lifetimeDetails: ProductDetails? = null

    // Owns background work that must outlive a single connect()/queryPurchases() call
    // (product-details fetch, fire-and-forget acknowledgement) without blocking the paywall's
    // 5 s budget. Dispatchers.Main, not IO: every Billing callback (setListener,
    // queryProductDetailsAsync, queryPurchasesAsync, acknowledgePurchase) already lands on the
    // main thread, and queryProducts() is itself callback-based (no blocking calls) — Main keeps
    // yearlyDetails/lifetimeDetails/_state/purchaseDeferred confined to one thread instead of
    // hopping between IO and Main. Cancelled in dispose().
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val client: BillingClient =
        BillingClient
            .newBuilder(context)
            .setListener { result, purchases ->
                handlePurchasesUpdate(result, purchases)
            }.enablePendingPurchases(
                PendingPurchasesParams.newBuilder().enableOneTimeProducts().build(),
            )
            // Billing 8: reconnects the service automatically after a transient disconnect, so a
            // query made right after backgrounding/foregrounding doesn't need a manual reconnect
            // dance (verified present on BillingClient.Builder via javap, 2026-09-24).
            .enableAutoServiceReconnection()
            .build()

    actual suspend fun connect() {
        suspendCancellableCoroutine<Unit> { cont ->
            client.startConnection(
                object : BillingClientStateListener {
                    override fun onBillingSetupFinished(result: BillingResult) {
                        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                            Log.w(TAG, "Billing setup failed: ${result.debugMessage}")
                        }
                        // Product-details fetch is network I/O and must not delay connect()'s
                        // return — the paywall decision only needs entitlement (queryPurchases),
                        // not prices. Runs on the client's own scope; formattedPrices still
                        // populates once it completes.
                        scope.launch { queryProducts() }
                        if (cont.isActive) cont.resume(Unit)
                    }

                    override fun onBillingServiceDisconnected() {
                        Log.w(TAG, "Billing service disconnected")
                    }
                },
            )
        }
    }

    private suspend fun queryProducts() {
        // Billing v8 requires same product type per query, so issue two separate calls.
        yearlyDetails = querySingleProduct(YEARLY_PRODUCT_ID, BillingClient.ProductType.SUBS)
        lifetimeDetails = querySingleProduct(LIFETIME_PRODUCT_ID, BillingClient.ProductType.INAPP)
        _formattedPrices.value =
            FormattedPrices(
                yearly =
                    yearlyDetails
                        ?.subscriptionOfferDetails
                        ?.firstOrNull()
                        ?.pricingPhases
                        ?.pricingPhaseList
                        ?.firstOrNull()
                        ?.formattedPrice,
                lifetime = lifetimeDetails?.oneTimePurchaseOfferDetails?.formattedPrice,
            )
    }

    private suspend fun querySingleProduct(
        productId: String,
        productType: String,
    ): ProductDetails? {
        val params =
            QueryProductDetailsParams
                .newBuilder()
                .setProductList(
                    listOf(
                        QueryProductDetailsParams.Product
                            .newBuilder()
                            .setProductId(productId)
                            .setProductType(productType)
                            .build(),
                    ),
                ).build()
        val (result, queryResult) =
            suspendCancellableCoroutine<Pair<BillingResult, QueryProductDetailsResult>> { cont ->
                client.queryProductDetailsAsync(params) { r, qr ->
                    if (cont.isActive) cont.resume(r to qr)
                }
            }
        return if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            queryResult.productDetailsList.orEmpty().firstOrNull { it.productId == productId }
        } else {
            Log.w(TAG, "queryProductDetails($productId) failed: ${result.debugMessage}")
            null
        }
    }

    /**
     * @return true once Play has answered both queries (regardless of what they found);
     * false if Play could not be reached, so the caller can distinguish "genuinely no
     * purchases" from "we don't actually know" (e.g. [BillingPremiumRepository.restore]).
     */
    actual suspend fun queryPurchases(): Boolean {
        val subs =
            suspendCancellableCoroutine<Pair<BillingResult, List<Purchase>>> { cont ->
                client.queryPurchasesAsync(
                    QueryPurchasesParams
                        .newBuilder()
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build(),
                ) { result, list -> if (cont.isActive) cont.resume(result to list) }
            }
        val inapp =
            suspendCancellableCoroutine<Pair<BillingResult, List<Purchase>>> { cont ->
                client.queryPurchasesAsync(
                    QueryPurchasesParams
                        .newBuilder()
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build(),
                ) { result, list -> if (cont.isActive) cont.resume(result to list) }
            }
        // A failed query must never wipe an existing purchase — leave _state untouched and let
        // the caller retry later (Play unreachable/not connected yields an empty list, which
        // would otherwise look identical to "genuinely no purchases").
        if (subs.first.responseCode != BillingClient.BillingResponseCode.OK ||
            inapp.first.responseCode != BillingClient.BillingResponseCode.OK
        ) {
            Log.w(
                TAG,
                "queryPurchases failed: subs=${subs.first.responseCode} inapp=${inapp.first.responseCode}",
            )
            return false
        }

        val verified = (subs.second + inapp.second).map { it to verifySignature(it) }
        val active =
            verified
                .firstOrNull { (p, ok) -> p.purchaseState == Purchase.PurchaseState.PURCHASED && ok }
                ?.first
        // Grant entitlement immediately — never gated on acknowledgement below succeeding.
        _state.value = active?.toPremiumState() ?: PremiumState.Free
        _purchasesQueried.value = true

        // A PURCHASED + verified purchase Play still thinks is unacknowledged must be
        // acknowledged here too, not only via the PurchasesUpdatedListener path — otherwise a
        // purchase that completed (or an app that died right after purchase, before ack) while
        // this client wasn't listening never gets acknowledged, and Play auto-refunds it after
        // 3 days. Re-checked on every query, so Play's 3-day grace window is retried repeatedly.
        // Fire-and-forget on the client's own scope — acknowledging can take a while and must
        // never delay this query's return (e.g. the "Restore purchases" toast).
        val needsAck =
            purchasesNeedingAcknowledgement(
                verified.map { (p, ok) -> AckCandidate(p.purchaseToken, p.purchaseState, ok, p.isAcknowledged) },
            )
        verified
            .filter { (p, _) -> p.purchaseToken in needsAck }
            .forEach { (p, _) -> scope.launch { acknowledgeAndLog(client, p) } }
        return true
    }

    actual suspend fun launchPurchase(
        activityContext: Any,
        tier: PremiumTier,
    ): PurchaseResult {
        val details =
            when (tier) {
                PremiumTier.YEARLY -> yearlyDetails
                PremiumTier.LIFETIME -> lifetimeDetails
            } ?: return PurchaseResult.Error("Product details not loaded")

        val flowParams =
            BillingFlowParams
                .newBuilder()
                .setProductDetailsParamsList(
                    listOf(
                        BillingFlowParams.ProductDetailsParams
                            .newBuilder()
                            .setProductDetails(details)
                            .apply {
                                if (tier == PremiumTier.YEARLY) {
                                    val token = details.subscriptionOfferDetails?.firstOrNull()?.offerToken
                                    if (token != null) setOfferToken(token)
                                }
                            }.build(),
                    ),
                ).build()

        if (purchaseDeferred != null) {
            return PurchaseResult.Error("Purchase already in flight")
        }
        val activity =
            activityContext as? Activity
                ?: return PurchaseResult.Error("launchPurchase requires Activity context")
        val deferred = CompletableDeferred<PurchaseResult>()
        purchaseDeferred = deferred
        val launchResult = client.launchBillingFlow(activity, flowParams)
        if (launchResult.responseCode != BillingClient.BillingResponseCode.OK) {
            purchaseDeferred = null
            return PurchaseResult.Error("launchBillingFlow failed: ${launchResult.debugMessage}")
        }
        return deferred.await()
    }

    private fun handlePurchasesUpdate(
        result: BillingResult,
        purchases: List<Purchase>?,
    ) {
        val deferred = purchaseDeferred
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                val list = purchases.orEmpty()
                val verified =
                    list.firstOrNull {
                        it.purchaseState == Purchase.PurchaseState.PURCHASED && verifySignature(it)
                    }
                if (verified != null) {
                    // Grant entitlement immediately — never gated on acknowledgement succeeding.
                    // Play gives 3 days to acknowledge, and queryPurchases() retries the
                    // acknowledgement on every call; a failed ack must never leave a paying user
                    // Free or make the purchase screen report an error.
                    _state.value = verified.toPremiumState()
                    deferred?.complete(PurchaseResult.Success)
                    if (deferred != null) purchaseDeferred = null
                    if (!verified.isAcknowledged) {
                        scope.launch { acknowledgeAndLog(client, verified) }
                    }
                } else {
                    deferred?.complete(PurchaseResult.Error("No verified purchase in callback"))
                    if (deferred != null) purchaseDeferred = null
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                deferred?.complete(PurchaseResult.UserCancelled)
                if (deferred != null) purchaseDeferred = null
            }
            else -> {
                deferred?.complete(PurchaseResult.Error(result.debugMessage))
                if (deferred != null) purchaseDeferred = null
            }
        }
    }

    private fun verifySignature(purchase: Purchase): Boolean {
        if (licensePublicKeyBase64.isBlank()) {
            Log.w(TAG, "PLAY_LICENSE_KEY blank — DEBUG-only signature bypass")
        }
        val result = verifyPlaySignature(purchase.originalJson, purchase.signature, licensePublicKeyBase64)
        if (!result && licensePublicKeyBase64.isNotBlank()) {
            Log.e(TAG, "Signature verification failed for purchase ${purchase.orderId}")
        }
        return result
    }

    private fun Purchase.toPremiumState(): PremiumState {
        val tier =
            when {
                products.contains(YEARLY_PRODUCT_ID) -> PremiumTier.YEARLY
                products.contains(LIFETIME_PRODUCT_ID) -> PremiumTier.LIFETIME
                else -> return PremiumState.Free
            }
        return PremiumState.Active(tier, Clock.System.now())
    }

    actual fun dispose() {
        // Cancel background work (in-flight acknowledgements, product-details fetch) before
        // tearing down the connection it depends on.
        scope.cancel()
        client.endConnection()
    }
}
