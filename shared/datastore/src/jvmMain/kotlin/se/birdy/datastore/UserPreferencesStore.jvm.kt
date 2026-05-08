package se.birdy.datastore

actual class UserPreferencesStore actual constructor(
    platformContext: Any?,
) {
    actual fun preferences(): UserPreferences = InMemoryUserPreferences()
}
