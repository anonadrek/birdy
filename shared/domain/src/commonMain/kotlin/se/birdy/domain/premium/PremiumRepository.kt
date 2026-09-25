package se.birdy.domain.premium

import kotlinx.coroutines.flow.StateFlow

interface PremiumRepository {
    /** Hot StateFlow — always emits the current premium state. */
    val state: StateFlow<PremiumState>

    /** Marks premium as purchased locally. Stub i v1 — verklig billing kommer senare. */
    suspend fun markPurchased(tier: PremiumTier)

    /**
     * Re-läser köp (för "Restore purchases"-knappen). Implementationer som är backade av en
     * verklig betaltjänst (t.ex. `BillingPremiumRepository` i composeApp) kan kasta
     * [BillingUnavailableException] om tjänsten inte gick att nå — det skiljer "inget köp
     * hittades" från "vi vet faktiskt inte".
     */
    suspend fun restore()
}
