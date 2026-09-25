package se.birdy.app.data.premium

import com.android.billingclient.api.Purchase
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import se.birdy.domain.premium.PremiumState
import se.birdy.domain.premium.PremiumTier

class PurchaseOutcomeTest {
    @Test
    fun `verified purchased wins outright`() {
        val candidate = OutcomeCandidate(purchaseState = Purchase.PurchaseState.PURCHASED, signatureOk = true)
        assertEquals(PurchaseResult.Success, purchaseUpdateOutcome(listOf(candidate)))
    }

    @Test
    fun `purchased but unverified signature is an error`() {
        val candidate = OutcomeCandidate(purchaseState = Purchase.PurchaseState.PURCHASED, signatureOk = false)
        assertEquals(
            PurchaseResult.Error("No verified purchase in callback"),
            purchaseUpdateOutcome(listOf(candidate)),
        )
    }

    @Test
    fun `pending only is pending`() {
        val candidate = OutcomeCandidate(purchaseState = Purchase.PurchaseState.PENDING, signatureOk = false)
        assertEquals(PurchaseResult.Pending, purchaseUpdateOutcome(listOf(candidate)))
    }

    @Test
    fun `verified purchased wins over a pending purchase in the same list`() {
        val purchased = OutcomeCandidate(purchaseState = Purchase.PurchaseState.PURCHASED, signatureOk = true)
        val pending = OutcomeCandidate(purchaseState = Purchase.PurchaseState.PENDING, signatureOk = false)
        assertEquals(PurchaseResult.Success, purchaseUpdateOutcome(listOf(pending, purchased)))
    }

    @Test
    fun `empty list is an error`() {
        assertEquals(PurchaseResult.Error("No verified purchase in callback"), purchaseUpdateOutcome(emptyList()))
    }

    @Test
    fun `already owned entitled is success`() {
        val result = alreadyOwnedOutcome(queryAnswered = true, entitled = true, hasPendingPurchase = false)
        assertEquals(PurchaseResult.Success, result)
    }

    @Test
    fun `already owned entitled is success even when the query was not answered`() {
        val result = alreadyOwnedOutcome(queryAnswered = false, entitled = true, hasPendingPurchase = false)
        assertEquals(PurchaseResult.Success, result)
    }

    @Test
    fun `already owned answered with a pending purchase and not entitled is pending`() {
        val result = alreadyOwnedOutcome(queryAnswered = true, entitled = false, hasPendingPurchase = true)
        assertEquals(PurchaseResult.Pending, result)
    }

    @Test
    fun `already owned not answered with a pending purchase is an error`() {
        val result = alreadyOwnedOutcome(queryAnswered = false, entitled = false, hasPendingPurchase = true)
        assertEquals(
            PurchaseResult.Error("ITEM_ALREADY_OWNED but no verified purchase after re-query"),
            result,
        )
    }

    @Test
    fun `already owned answered with no pending purchase and not entitled is an error`() {
        val result = alreadyOwnedOutcome(queryAnswered = true, entitled = false, hasPendingPurchase = false)
        assertEquals(
            PurchaseResult.Error("ITEM_ALREADY_OWNED but no verified purchase after re-query"),
            result,
        )
    }

    @Test
    fun `free to free is unchanged`() {
        assertFalse(entitlementChanged(PremiumState.Free, PremiumState.Free))
    }

    @Test
    fun `free to active is changed`() {
        val active = PremiumState.Active(PremiumTier.YEARLY, Instant.fromEpochMilliseconds(0))
        assertTrue(entitlementChanged(PremiumState.Free, active))
    }

    @Test
    fun `active to free is changed`() {
        val active = PremiumState.Active(PremiumTier.YEARLY, Instant.fromEpochMilliseconds(0))
        assertTrue(entitlementChanged(active, PremiumState.Free))
    }

    @Test
    fun `same tier with a different purchasedAt is unchanged`() {
        val current = PremiumState.Active(PremiumTier.YEARLY, Instant.fromEpochMilliseconds(0))
        val updated = PremiumState.Active(PremiumTier.YEARLY, Instant.fromEpochMilliseconds(999_999))
        assertFalse(entitlementChanged(current, updated))
    }

    @Test
    fun `different tier is changed`() {
        val current = PremiumState.Active(PremiumTier.YEARLY, Instant.fromEpochMilliseconds(0))
        val updated = PremiumState.Active(PremiumTier.LIFETIME, Instant.fromEpochMilliseconds(0))
        assertTrue(entitlementChanged(current, updated))
    }
}
