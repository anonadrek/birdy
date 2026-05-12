package se.birdy.datastore

import se.birdy.domain.premium.DebugPremiumOverrides
import se.birdy.domain.premium.PremiumRepository

/**
 * Platform factory. Konstruktor-arg är opaque (`Any?`) — Android behöver Context,
 * iOS/JVM inte. Mönstret matchar `UserPreferencesStore` + `DatabaseFactory`.
 *
 * Android: pass `applicationContext`.
 * iOS: pass null (kastar NotImplementedError i v1).
 * JVM (test): pass null (in-memory impl).
 */
expect class PremiumStateStore(
    platformContext: Any?,
) {
    fun repository(): PremiumRepository

    /**
     * Debug-only handle to [DebugPremiumOverrides] — never call from production code.
     * Wired only in tests and in `MainActivity` debug-builds via `BuildConfig.PREMIUM_DEBUG_FORCE_*`.
     */
    fun debugOverrides(): DebugPremiumOverrides
}
