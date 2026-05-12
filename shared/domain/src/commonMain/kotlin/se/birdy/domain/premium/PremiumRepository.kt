package se.birdy.domain.premium

import kotlinx.coroutines.flow.StateFlow

interface PremiumRepository {
    /** Hot StateFlow — always emits the current premium state. */
    val state: StateFlow<PremiumState>

    /** Marks premium as purchased locally. Stub i v1 — verklig billing kommer senare. */
    suspend fun markPurchased(tier: PremiumTier)

    /** Re-läser DataStore (för "Restore purchases"-knapp). Stub i v1. */
    suspend fun restore()

    /** Test-only / debug-only — sätter state direkt utan att gå via DataStore. */
    suspend fun forceState(state: PremiumState)
}
