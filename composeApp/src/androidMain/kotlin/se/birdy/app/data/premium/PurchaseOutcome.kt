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

/**
 * How much a purchases-updated [granted] entitlement should bump `listenerGrants`
 * ([PremiumBillingClient]'s marker of fresh listener evidence that a racing
 * [PremiumBillingClient.queryPurchases] must not overwrite with an older answer): 1 whenever it
 * actually resolved to Active, 0 otherwise. Deliberately independent of [entitlementChanged] —
 * this listener can legitimately fire more than once for the same purchase (e.g. Billing 8
 * echoing a non-OK launchBillingFlow result back to the listener), and even a repeat grant of an
 * unchanged tier is still newer evidence than a query that started before it landed, so
 * suppressing that racing query is free (state is already Active for that tier) while failing to
 * suppress it could let a stale query write Free over a real entitlement.
 */
internal fun listenerGrantIncrement(granted: PremiumState): Int = if (granted is PremiumState.Active) 1 else 0

/**
 * The entitlement [PremiumBillingClient]'s `_state` should hold after a purchases-updated
 * [granted] result: [granted] itself only when it actually resolved to Active AND
 * [entitlementChanged] says it's genuinely new — never for a [granted] that resolved to Free
 * (e.g. a verified purchase of an unrecognised product), which must never downgrade a real
 * entitlement, and never for a repeat grant of the same tier, so toPremiumState()'s fresh
 * purchasedAt stamp doesn't make an unchanged Active look "new" on every redelivery. [current]
 * unchanged otherwise (a self-assignment MutableStateFlow.value will not re-emit for).
 */
internal fun nextStateAfterListenerGrant(
    current: PremiumState,
    granted: PremiumState,
): PremiumState = if (granted is PremiumState.Active && entitlementChanged(current, granted)) granted else current
