package se.birdy.app.data.premium

import com.android.billingclient.api.Purchase
import se.birdy.domain.premium.PremiumState

/**
 * Pure decision for an OK purchases-updated callback, extracted for unit testability without the
 * Billing SDK: a PURCHASED + verified purchase wins; otherwise a PENDING one means Play accepted the
 * order but payment isn't confirmed yet; anything else is an error.
 */
internal fun purchaseUpdateOutcome(candidates: List<OutcomeCandidate>): PurchaseResult =
    when {
        candidates.any { it.purchaseState == Purchase.PurchaseState.PURCHASED && it.signatureOk } ->
            PurchaseResult.Success
        candidates.any { it.purchaseState == Purchase.PurchaseState.PENDING } -> PurchaseResult.Pending
        else -> PurchaseResult.Error("No verified purchase in callback")
    }

/** One purchase's outcome-relevant fields, stripped of every Billing SDK type. */
internal data class OutcomeCandidate(
    val purchaseState: Int,
    val signatureOk: Boolean,
)

/**
 * Pure decision after Play answered ITEM_ALREADY_OWNED and purchases were queried again. Play says
 * the user already owns it, so a confirmed entitlement completes the purchase screen and a pending
 * purchase shows as pending. Only when neither is confirmed (Play unreachable, or the owned purchase
 * failed signature verification) is it an error.
 */
internal fun alreadyOwnedOutcome(
    queryAnswered: Boolean,
    entitled: Boolean,
    hasPendingPurchase: Boolean,
): PurchaseResult =
    when {
        entitled -> PurchaseResult.Success
        queryAnswered && hasPendingPurchase -> PurchaseResult.Pending
        else -> PurchaseResult.Error("ITEM_ALREADY_OWNED but no verified purchase after re-query")
    }

/**
 * True when [updated] represents a different entitlement than [current] (Free <-> Active, or a
 * different tier). [PremiumState.Active.purchasedAt] is deliberately ignored: it is re-stamped on
 * every successful [PremiumBillingClient.queryPurchases] call, so comparing it directly would make
 * an unchanged entitlement look "new" on every foreground re-check.
 */
internal fun entitlementChanged(
    current: PremiumState,
    updated: PremiumState,
): Boolean =
    when (updated) {
        is PremiumState.Free -> current !is PremiumState.Free
        is PremiumState.Active -> current !is PremiumState.Active || current.tier != updated.tier
    }
