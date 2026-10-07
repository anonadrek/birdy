package se.birdy.app.i18n

import org.jetbrains.compose.resources.ResourceEnvironment
import org.jetbrains.compose.resources.getSystemResourceEnvironment
import se.birdy.content.Locale

private val environmentLock = Any()

/**
 * compose-resources reads the language of its system environment from
 * `java.util.Locale.getDefault()`, so the default is set to [locale] for the one call and put
 * back. The lock keeps two of these calls from restoring each other's value; another thread
 * reading the default in between sees the app's own language. [AppStrings] builds it once.
 */
internal actual fun resourceEnvironmentFor(locale: Locale): ResourceEnvironment =
    synchronized(environmentLock) {
        val previous = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag(locale.code))
            getSystemResourceEnvironment()
        } finally {
            java.util.Locale.setDefault(previous)
        }
    }
