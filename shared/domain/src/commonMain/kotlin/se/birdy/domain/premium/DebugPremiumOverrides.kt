package se.birdy.domain.premium

/**
 * Debug-only contract for forcing [PremiumState] without going through [PremiumRepository.markPurchased].
 *
 * Implementations live alongside [PremiumRepository] impls, but this interface is **never** exposed
 * to production call-sites — wired only in tests and in `MainActivity` debug-builds via
 * `BuildConfig.PREMIUM_DEBUG_FORCE_*` flags. Keeping it on a sibling interface (vs. on
 * [PremiumRepository] itself) prevents accidental bypass of the real purchase flow once Play
 * Billing replaces the v1 stub.
 */
interface DebugPremiumOverrides {
    /** Test/debug-only — sätter state direkt utan att gå via DataStore. */
    suspend fun forceState(state: PremiumState)
}
