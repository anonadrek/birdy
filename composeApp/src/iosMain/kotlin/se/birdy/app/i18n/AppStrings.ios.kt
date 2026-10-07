package se.birdy.app.i18n

import org.jetbrains.compose.resources.ResourceEnvironment
import org.jetbrains.compose.resources.getSystemResourceEnvironment
import se.birdy.content.Locale

/**
 * On iOS the whole UI follows the phone's language: there is no in-app language override yet
 * (`applyLocale` is a no-op on iOS), and compose-resources reads `NSLocale.preferredLanguages`
 * both inside and outside composition. The system environment therefore already is the language
 * the iOS UI shows, so notifications match the screens. When iOS gets the in-app picker, this
 * actual has to follow [locale] too.
 */
internal actual fun resourceEnvironmentFor(locale: Locale): ResourceEnvironment = getSystemResourceEnvironment()
