package se.birdy.datastore

/**
 * Platform factory. Constructor-arg är opaque (`Any?`) eftersom Android behöver
 * Context men iOS/JVM inte gör det. Mönstret matchar `DatabaseFactory` i :shared:data.
 *
 * Android: pass `applicationContext`.
 * iOS: pass null (kastar NotImplementedError i v1).
 * JVM (test): pass null (in-memory impl).
 */
expect class UserPreferencesStore(
    platformContext: Any?,
) {
    fun preferences(): UserPreferences
}
