package se.birdy.datastore

/**
 * iOS actual. `platformContext` is unused (iOS needs no Context) — pass null.
 * Returns an NSUserDefaults-backed [UserPreferences] (persistent across relaunch).
 */
actual class UserPreferencesStore actual constructor(
    platformContext: Any?,
) {
    actual fun preferences(): UserPreferences = NsUserDefaultsUserPreferences()
}
