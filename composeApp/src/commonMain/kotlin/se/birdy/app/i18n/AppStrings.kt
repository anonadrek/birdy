package se.birdy.app.i18n

import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.ResourceEnvironment
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import se.birdy.content.Locale

/**
 * compose-resources strings read outside composition (the notifications, the weekly recap's
 * stamps, the PDF export) in the app's language [locale].
 *
 * `getString(resource)` without an environment reads the platform default, which on Android is
 * `java.util.Locale.getDefault()`: the phone's language, not the one picked in Birdy. With
 * Birdy in Svenska on an English phone, Dagens fågel said "Bird of the day: …" and the weekly
 * recap's stamps were English (QA 2026-10-07). Inside composition `stringResource` already
 * follows the app's language, so only these paths needed it.
 */
class AppStrings(
    val locale: Locale,
) {
    private val environment: ResourceEnvironment by lazy { resourceEnvironmentFor(locale) }

    suspend fun get(resource: StringResource): String = getString(environment, resource)

    suspend fun get(
        resource: StringResource,
        vararg formatArgs: Any,
    ): String = getString(environment, resource, *formatArgs)

    suspend fun plural(
        resource: PluralStringResource,
        quantity: Int,
        vararg formatArgs: Any,
    ): String = getPluralString(environment, resource, quantity, *formatArgs)
}

/**
 * A compose-resources environment whose language is [locale]; theme and density are the
 * system's (they don't affect strings). compose-resources 1.8 has no public constructor for
 * [ResourceEnvironment], so each platform builds it from its own system environment.
 */
internal expect fun resourceEnvironmentFor(locale: Locale): ResourceEnvironment
