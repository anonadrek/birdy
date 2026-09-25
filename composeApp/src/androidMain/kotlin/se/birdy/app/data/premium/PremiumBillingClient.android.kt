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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
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

// Retry backoff for a still-missing price after a product-details fetch (e.g. a flaky/offline
// cold start): 2 s, then 5 s, then 15 s. Each named separately so the literals count as constant
// declarations for detekt's MagicNumber rule instead of magic numbers inside a listOf(...) call.
private const val PRICE_RETRY_DELAY_1_MS = 2_000L
private const val PRICE_RETRY_DELAY_2_MS = 5_000L
private const val PRICE_RETRY_DELAY_3_MS = 15_000L
private val PRICE_RETRY_BACKOFF_MS = listOf(PRICE_RETRY_DELAY_1_MS, PRICE_RETRY_DELAY_2_MS, PRICE_RETRY_DELAY_3_MS)

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

/**
 * The subscription offer Play considers the base plan (no offerId), falling back to the first
 * offer if none is explicitly the base plan. We show and sell the base plan, so the renewal
 * text always matches what the user buys, even if an intro/trial offer is ever added in Console.
 */
private fun ProductDetails.basePlanOffer(): ProductDetails.SubscriptionOfferDetails? =
    subscriptionOfferDetails?.firstOrNull { it.offerId == null } ?: subscriptionOfferDetails?.firstOrNull()

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

    /** True when the last successful [queryPurchases] saw a PENDING purchase (cash/delayed payment). */
    private var hasPendingPurchase = false

    /**
     * Bumped every time [handlePurchasesUpdate] grants an entitlement. [queryPurchases] reads
     * this before/after its own network round trip to detect whether the listener granted an
     * entitlement while the query was in flight — the app now re-queries on every foreground, so
     * the two race. When they do, the listener's answer is newer and must not be overwritten by
     * the query's (possibly stale) one.
     */
    private var listenerGrants = 0

    /**
     * Tracks the in-flight [queryProducts] fetch, if any, so [connect] and [queryPurchases]
     * (which can both want a fresh price fetch) never pile up a second concurrent one.
     */
    private var productsJob: Job? = null

    // Owns background work that must outlive a single connect()/queryPurchases() call
    // (product-details fetch, fire-and-forget acknowledgement) without blocking the paywall's
    // 5 s budget. Dispatchers.Main, not IO: every Billing callback (setListener,
    // queryProductDetailsAsync, queryPurchasesAsync, acknowledgePurchase) already lands on the
    // main thread, and queryProducts() is itself callback-based (no blocking calls) — Main keeps
    // yearlyDetails/lifetimeDetails/_state/purchaseDeferred/hasPendingPurchase/listenerGrants
    // confined to one thread instead of hopping between IO and Main. Cancelled in dispose().
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
                            Log.w(
                                TAG,
                                "Billing setup failed: responseCode=${result.responseCode} ${result.debugMessage}",
                            )
                        }
                        // Product-details fetch is network I/O and must not delay connect()'s
                        // return — the paywall decision only needs entitlement (queryPurchases),
                        // not prices. Runs on the client's own scope; formattedPrices still
                        // populates once it completes.
                        if (productsJob?.isActive != true) {
                            productsJob = scope.launch { queryProducts() }
                        }
                        if (cont.isActive) cont.resume(Unit)
                    }

                    override fun onBillingServiceDisconnected() {
                        Log.w(TAG, "Billing service disconnected")
                    }
                },
            )
        }
    }

    /**
     * Fetches both product prices, retrying a still-missing one with [PRICE_RETRY_BACKOFF_MS]
     * backoff (2 s, 5 s, 15 s) — a flaky/offline cold start must not leave the purchase screen
     * stuck on "Loading price…" until the next foreground. Runs entirely inside the caller's
     * single [productsJob] launch, so [connect] and [queryPurchases] still never race a second
     * concurrent fetch while retries are pending.
     */
    private suspend fun queryProducts() {
        // Billing v8 requires same product type per query, so issue two separate calls. A
        // failed or empty fetch keeps the previous details (Elvis fallback) — a transient
        // failure must never erase a price that was already loaded.
        suspend fun fetchPrices() {
            yearlyDetails = querySingleProduct(YEARLY_PRODUCT_ID, BillingClient.ProductType.SUBS) ?: yearlyDetails
            lifetimeDetails =
                querySingleProduct(LIFETIME_PRODUCT_ID, BillingClient.ProductType.INAPP) ?: lifetimeDetails
            _formattedPrices.value =
                FormattedPrices(
                    yearly =
                        yearlyDetails
                            ?.basePlanOffer()
                            ?.pricingPhases
                            ?.pricingPhaseList
                            // Recurring phase, not an intro/trial one — matches what launchPurchase() buys.
                            ?.lastOrNull()
                            ?.formattedPrice,
                    lifetime = lifetimeDetails?.oneTimePurchaseOfferDetails?.formattedPrice,
                )
        }

        fun bothPricesLoaded() = _formattedPrices.value.yearly != null && _formattedPrices.value.lifetime != null

        fetchPrices()
        for (backoffMs in PRICE_RETRY_BACKOFF_MS) {
            if (bothPricesLoaded()) return
            delay(backoffMs)
            fetchPrices()
        }
        if (!bothPricesLoaded()) {
            val missing =
                buildList {
                    if (_formattedPrices.value.yearly == null) add(YEARLY_PRODUCT_ID)
                    if (_formattedPrices.value.lifetime == null) add(LIFETIME_PRODUCT_ID)
                }
            Log.w(TAG, "Price(s) still missing after retries: $missing")
        }
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
            val details = queryResult.productDetailsList.orEmpty().firstOrNull { it.productId == productId }
            if (details == null) {
                // OK-but-missing means Play doesn't know this product — most likely it isn't
                // configured/active in Play Console yet. Silent otherwise: nothing would ever
                // flag a Console typo or an unpublished product.
                val unfetched = queryResult.unfetchedProductList.joinToString { "${it.productId}:${it.statusCode}" }
                Log.w(
                    TAG,
                    "queryProductDetails($productId) returned no match (unfetched=[$unfetched]) — " +
                        "check the product is configured and active in Play Console.",
                )
            }
            details
        } else {
            Log.w(
                TAG,
                "queryProductDetails($productId) failed: responseCode=${result.responseCode} ${result.debugMessage}",
            )
            null
        }
    }

    /**
     * @return true once Play has answered both queries (regardless of what they found);
     * false if Play could not be reached, so the caller can distinguish "genuinely no
     * purchases" from "we don't actually know" (e.g. [BillingPremiumRepository.restore]).
     */
    actual suspend fun queryPurchases(): Boolean {
        // See the listenerGrants KDoc: captured before the network round trip so a grant that
        // lands while it's in flight is detected below rather than clobbered.
        val grantsBefore = listenerGrants
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
        hasPendingPurchase = (subs.second + inapp.second).any { it.purchaseState == Purchase.PurchaseState.PENDING }
        // The purchases-updated listener can grant an entitlement while this query is in flight
        // (the app now re-queries on every foreground). When that happened, its answer is newer
        // than this query's and must win — this query must not overwrite it with a stale Free.
        if (grantsBefore == listenerGrants) {
            val newState = active?.toPremiumState() ?: PremiumState.Free
            // toPremiumState() re-stamps purchasedAt on every call — entitlementChanged() ignores
            // that field, so an unchanged Active doesn't look "new" on every foreground re-check.
            if (entitlementChanged(_state.value, newState)) {
                _state.value = newState
            }
        }
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

        // Prices are otherwise only fetched once at connect() — an offline cold start would
        // leave the purchase screen on "Loading price…" with a disabled buy button until restart.
        val missingPrice = _formattedPrices.value.yearly == null || _formattedPrices.value.lifetime == null
        if (missingPrice && productsJob?.isActive != true) {
            productsJob = scope.launch { queryProducts() }
        }
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
                                    // Buy the same base-plan offer that basePlanOffer() showed the
                                    // price for, so the purchase always matches what the user saw.
                                    val token = details.basePlanOffer()?.offerToken
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
        // A throw here (rather than a non-OK BillingResult) must not leave purchaseDeferred set —
        // a stale deferred would fail every later purchase as "already in flight" forever.
        val launchResult =
            runCatching { client.launchBillingFlow(activity, flowParams) }
                .onFailure { purchaseDeferred = null }
                .getOrThrow()
        if (launchResult.responseCode != BillingClient.BillingResponseCode.OK) {
            purchaseDeferred = null
            return if (launchResult.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
                resolveAlreadyOwned()
            } else {
                val code = launchResult.responseCode
                PurchaseResult.Error("launchBillingFlow failed $code: ${launchResult.debugMessage}")
            }
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
                // Verify each purchase's signature once — reused both to grant the entitlement
                // below and to decide the outcome via the pure purchaseUpdateOutcome().
                val checked = purchases.orEmpty().map { it to verifySignature(it) }
                val verified =
                    checked
                        .firstOrNull { (p, ok) -> p.purchaseState == Purchase.PurchaseState.PURCHASED && ok }
                        ?.first
                if (verified != null) {
                    // Grant entitlement immediately — never gated on acknowledgement succeeding.
                    // Play gives 3 days to acknowledge, and queryPurchases() retries the
                    // acknowledgement on every call; a failed ack must never leave a paying user
                    // Free or make the purchase screen report an error.
                    val granted = verified.toPremiumState()
                    // See shouldWriteListenerGrant's KDoc: only a new Active entitlement is
                    // written + bumps listenerGrants — this listener can legitimately fire more
                    // than once for the same purchase (see the ITEM_ALREADY_OWNED echo below).
                    if (shouldWriteListenerGrant(_state.value, granted)) {
                        _state.value = granted
                        listenerGrants++
                    }
                    // Acknowledgement is unconditional — independent of whether the entitlement
                    // looked "new" above; Play still needs the ack regardless.
                    if (!verified.isAcknowledged) {
                        scope.launch { acknowledgeAndLog(client, verified) }
                    }
                }
                val outcome =
                    purchaseUpdateOutcome(checked.map { (p, ok) -> OutcomeCandidate(p.purchaseState, ok) })
                if (outcome is PurchaseResult.Pending) {
                    // Cash/delayed payment method: Play accepted the order but hasn't confirmed
                    // payment yet. Not an error — entitlement arrives later via `state` or the
                    // next queryPurchases() once Play confirms it.
                    Log.i(TAG, "Purchase pending: Play accepted the order, payment not confirmed yet")
                }
                deferred?.complete(outcome)
                if (deferred != null) purchaseDeferred = null
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                deferred?.complete(PurchaseResult.UserCancelled)
                if (deferred != null) purchaseDeferred = null
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                // Most common trigger: Billing 8 echoes every non-OK launchBillingFlow result
                // back to this listener too, and launchPurchase() already cleared
                // purchaseDeferred for that same ITEM_ALREADY_OWNED — so deferred is null here
                // and the echo just refreshes state via the else branch below. Also reachable
                // directly: tapping buy again while a pending purchase waits, or a paying user
                // whose startup query hasn't answered yet. Play's guidance is to re-query
                // purchases rather than surface this as a purchase error.
                if (deferred != null) {
                    purchaseDeferred = null
                    scope.launch { deferred.complete(resolveAlreadyOwned()) }
                } else {
                    scope.launch { queryPurchases() }
                }
            }
            else -> {
                val code = result.responseCode
                deferred?.complete(PurchaseResult.Error("onPurchasesUpdated $code: ${result.debugMessage}"))
                if (deferred != null) purchaseDeferred = null
            }
        }
    }

    /**
     * Re-queries purchases after Play answered ITEM_ALREADY_OWNED (from either
     * [handlePurchasesUpdate] or [launchPurchase]) and turns the fresh answer into a
     * [PurchaseResult] via the pure [alreadyOwnedOutcome].
     */
    private suspend fun resolveAlreadyOwned(): PurchaseResult =
        alreadyOwnedOutcome(
            queryAnswered = queryPurchases(),
            entitled = _state.value is PremiumState.Active,
            hasPendingPurchase = hasPendingPurchase,
        )

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
