package se.birdy.app.i18n

import se.birdy.datastore.AppLanguage

/** BCP-47 tag, or null for "follow system". Used by [LocaleResolver] override. */
fun AppLanguage.toLocaleTagOrNull(): String? =
    when (this) {
        AppLanguage.SV -> "sv"
        AppLanguage.EN -> "en"
        AppLanguage.SYSTEM -> null
    }

/** BCP-47 tag, or empty string for "follow system". Used by `LocaleListCompat.forLanguageTags`. */
fun AppLanguage.toLocaleTagOrEmpty(): String = toLocaleTagOrNull().orEmpty()
