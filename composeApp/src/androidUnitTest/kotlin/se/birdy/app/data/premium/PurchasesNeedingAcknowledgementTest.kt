package se.birdy.app.data.premium

import com.android.billingclient.api.Purchase
import org.junit.Assert.assertEquals
import org.junit.Test

class PurchasesNeedingAcknowledgementTest {
    @Test
    fun `purchased verified and unacknowledged needs ack`() {
        val candidate = ackCandidate(isAcknowledged = false)
        assertEquals(setOf(candidate.purchaseToken), purchasesNeedingAcknowledgement(listOf(candidate)))
    }

    @Test
    fun `already acknowledged is never re-acknowledged`() {
        val candidate = ackCandidate(isAcknowledged = true)
        assertEquals(emptySet<String>(), purchasesNeedingAcknowledgement(listOf(candidate)))
    }

    @Test
    fun `unverified signature is not acknowledged`() {
        val candidate = ackCandidate(signatureOk = false, isAcknowledged = false)
        assertEquals(emptySet<String>(), purchasesNeedingAcknowledgement(listOf(candidate)))
    }

    @Test
    fun `pending purchase is not acknowledged`() {
        val candidate =
            ackCandidate(purchaseState = Purchase.PurchaseState.PENDING, isAcknowledged = false)
        assertEquals(emptySet<String>(), purchasesNeedingAcknowledgement(listOf(candidate)))
    }

    @Test
    fun `only the purchases that need it are returned from a mixed list`() {
        val needsAck = ackCandidate(id = "needs-ack", isAcknowledged = false)
        val alreadyAcked = ackCandidate(id = "already-acked", isAcknowledged = true)
        val pending = ackCandidate(id = "pending", purchaseState = Purchase.PurchaseState.PENDING, isAcknowledged = false)
        val unverified = ackCandidate(id = "unverified", signatureOk = false, isAcknowledged = false)

        val result = purchasesNeedingAcknowledgement(listOf(needsAck, alreadyAcked, pending, unverified))

        assertEquals(setOf("needs-ack"), result)
    }

    private fun ackCandidate(
        id: String = "token",
        purchaseState: Int = Purchase.PurchaseState.PURCHASED,
        signatureOk: Boolean = true,
        isAcknowledged: Boolean,
    ) = AckCandidate(
        purchaseToken = id,
        purchaseState = purchaseState,
        signatureOk = signatureOk,
        isAcknowledged = isAcknowledged,
    )
}
