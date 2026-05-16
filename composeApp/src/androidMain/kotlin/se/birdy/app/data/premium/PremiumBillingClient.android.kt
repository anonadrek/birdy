package se.birdy.app.data.premium

import android.app.Activity
import android.content.Context
import android.util.Base64
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

private const val TAG = "PremiumBilling"
private const val YEARLY_PRODUCT_ID = "premium_yearly_v1"
private const val LIFETIME_PRODUCT_ID = "premium_lifetime_v1"

actual class PremiumBillingClient(
    private val context: Context,
    private val licensePublicKeyBase64: String,
) {
    private val _state = MutableStateFlow<PremiumState>(PremiumState.Free)
    actual val state: StateFlow<PremiumState> = _state.asStateFlow()

    private val _formattedPrices = MutableStateFlow(FormattedPrices())
    actual val formattedPrices: StateFlow<FormattedPrices> = _formattedPrices.asStateFlow()

    private var purchaseDeferred: CompletableDeferred<PurchaseResult>? = null
    private var yearlyDetails: ProductDetails? = null
    private var lifetimeDetails: ProductDetails? = null

    private val client: BillingClient =
        BillingClient
            .newBuilder(context)
            .setListener { result, purchases ->
                handlePurchasesUpdate(result, purchases)
            }.enablePendingPurchases(
                PendingPurchasesParams.newBuilder().enableOneTimeProducts().build(),
            ).build()

    actual suspend fun connect() {
        suspendCancellableCoroutine<Unit> { cont ->
            client.startConnection(
                object : BillingClientStateListener {
                    override fun onBillingSetupFinished(result: BillingResult) {
                        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                            if (cont.isActive) cont.resume(Unit)
                        } else {
                            Log.w(TAG, "Billing setup failed: ${result.debugMessage}")
                            if (cont.isActive) cont.resume(Unit)
                        }
                    }

                    override fun onBillingServiceDisconnected() {
                        Log.w(TAG, "Billing service disconnected")
                    }
                },
            )
        }
        queryProducts()
    }

    private suspend fun queryProducts() {
        val params =
            QueryProductDetailsParams
                .newBuilder()
                .setProductList(
                    listOf(
                        QueryProductDetailsParams.Product
                            .newBuilder()
                            .setProductId(YEARLY_PRODUCT_ID)
                            .setProductType(BillingClient.ProductType.SUBS)
                            .build(),
                        QueryProductDetailsParams.Product
                            .newBuilder()
                            .setProductId(LIFETIME_PRODUCT_ID)
                            .setProductType(BillingClient.ProductType.INAPP)
                            .build(),
                    ),
                ).build()
        val (result, queryResult) =
            suspendCancellableCoroutine<Pair<BillingResult, QueryProductDetailsResult>> { cont ->
                client.queryProductDetailsAsync(params) { r, qr ->
                    if (cont.isActive) cont.resume(r to qr)
                }
            }
        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            val details = queryResult.productDetailsList.orEmpty()
            yearlyDetails = details.firstOrNull { it.productId == YEARLY_PRODUCT_ID }
            lifetimeDetails = details.firstOrNull { it.productId == LIFETIME_PRODUCT_ID }
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
        } else {
            Log.w(TAG, "queryProductDetails failed: ${result.debugMessage}")
        }
    }

    actual suspend fun queryPurchases() {
        val subsResult =
            suspendCancellableCoroutine<List<Purchase>> { cont ->
                client.queryPurchasesAsync(
                    QueryPurchasesParams
                        .newBuilder()
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build(),
                ) { _, list -> if (cont.isActive) cont.resume(list) }
            }
        val inappResult =
            suspendCancellableCoroutine<List<Purchase>> { cont ->
                client.queryPurchasesAsync(
                    QueryPurchasesParams
                        .newBuilder()
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build(),
                ) { _, list -> if (cont.isActive) cont.resume(list) }
            }
        val active =
            (subsResult + inappResult).firstOrNull { p ->
                p.purchaseState == Purchase.PurchaseState.PURCHASED && verifySignature(p)
            }
        _state.value = active?.toPremiumState() ?: PremiumState.Free
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

        val deferred = CompletableDeferred<PurchaseResult>()
        purchaseDeferred = deferred
        val launchResult = client.launchBillingFlow(activityContext as Activity, flowParams)
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
        purchaseDeferred = null
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                val list = purchases.orEmpty()
                val verified =
                    list.firstOrNull {
                        it.purchaseState == Purchase.PurchaseState.PURCHASED && verifySignature(it)
                    }
                if (verified != null) {
                    if (!verified.isAcknowledged) {
                        acknowledge(verified) { ackResult ->
                            if (ackResult.responseCode == BillingClient.BillingResponseCode.OK) {
                                _state.value = verified.toPremiumState()
                                deferred?.complete(PurchaseResult.Success)
                            } else {
                                deferred?.complete(PurchaseResult.Error("ack failed: ${ackResult.debugMessage}"))
                            }
                        }
                    } else {
                        _state.value = verified.toPremiumState()
                        deferred?.complete(PurchaseResult.Success)
                    }
                } else {
                    deferred?.complete(PurchaseResult.Error("No verified purchase in callback"))
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                deferred?.complete(PurchaseResult.UserCancelled)
            }
            else -> {
                deferred?.complete(PurchaseResult.Error(result.debugMessage))
            }
        }
    }

    private fun acknowledge(
        purchase: Purchase,
        callback: (BillingResult) -> Unit,
    ) {
        val params =
            AcknowledgePurchaseParams
                .newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
        client.acknowledgePurchase(params) { result -> callback(result) }
    }

    private fun verifySignature(purchase: Purchase): Boolean {
        if (licensePublicKeyBase64.isBlank()) {
            Log.w(TAG, "PLAY_LICENSE_KEY missing; skipping signature verification (debug only)")
            return BuildConfig.DEBUG
        }
        return try {
            val keyBytes = Base64.decode(licensePublicKeyBase64, Base64.DEFAULT)
            val publicKey: PublicKey =
                KeyFactory
                    .getInstance("RSA")
                    .generatePublic(X509EncodedKeySpec(keyBytes))
            val sig = Signature.getInstance("SHA1withRSA")
            sig.initVerify(publicKey)
            sig.update(purchase.originalJson.toByteArray())
            val sigBytes = Base64.decode(purchase.signature, Base64.DEFAULT)
            sig.verify(sigBytes)
        } catch (t: Throwable) {
            Log.e(TAG, "Signature verification failed", t)
            false
        }
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
        client.endConnection()
    }
}
