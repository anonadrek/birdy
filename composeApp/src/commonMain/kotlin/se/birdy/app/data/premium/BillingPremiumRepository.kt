package se.birdy.app.data.premium

import kotlinx.coroutines.flow.StateFlow
import se.birdy.domain.premium.PremiumRepository
import se.birdy.domain.premium.PremiumState
import se.birdy.domain.premium.PremiumTier

/** Thrown by [BillingPremiumRepository.restore] when the platform billing service could not be
 * reached (e.g. Play unreachable) — as opposed to a successful query that simply found nothing.
 */
class BillingUnavailableException : Exception("Google Play billing unavailable")

/**
 * Plan 6b1: replaces the DataStore-only stub from Plan 7e.
 * - `state` is sourced from the wrapped PremiumBillingClient
 * - `markPurchased` is a no-op here; UI flow drives launchPurchase via PremiumViewModel.purchase()
 *   which sees state changes through `state` flow.
 * - `restore` triggers a fresh queryPurchases.
 */
class BillingPremiumRepository(
    override val state: StateFlow<PremiumState>,
    private val queryPurchases: suspend () -> Boolean,
) : PremiumRepository {
    override suspend fun markPurchased(tier: PremiumTier) {
        // No-op: the real purchase flow runs through PremiumBillingClient.launchPurchase,
        // and state propagates via the wrapped StateFlow.
    }

    /** @throws BillingUnavailableException if the platform billing service could not be reached. */
    override suspend fun restore() {
        if (!queryPurchases()) throw BillingUnavailableException()
    }
}
