package se.birdy.datastore

actual class UserPreferencesStore actual constructor(platformContext: Any?) {
    actual fun preferences(): UserPreferences =
        throw NotImplementedError(
            "iOS UserPreferencesStore not implemented in v1 — see Plan 7a deviation #2 + spec §13.",
        )
}
