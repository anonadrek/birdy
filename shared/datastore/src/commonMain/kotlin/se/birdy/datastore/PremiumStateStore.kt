package se.birdy.datastore

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
}
