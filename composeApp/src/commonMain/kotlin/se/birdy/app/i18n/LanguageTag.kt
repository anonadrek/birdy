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

/**
 * The app language a per-app locale list stands for (Android 13+ `LocaleManager.applicationLocales`
 * as `toLanguageTags()`, e.g. "sv-SE,en-US"): the first tag's language, or [AppLanguage.SYSTEM] for an
 * empty list or a language Birdy doesn't have. The inverse of [toLocaleTagOrEmpty].
 */
fun appLanguageFromLocaleTags(tags: String): AppLanguage =
    when (
        tags
            .trim()
            .substringBefore(',')
            .substringBefore('-')
            .lowercase()
    ) {
        "sv" -> AppLanguage.SV
        "en" -> AppLanguage.EN
        else -> AppLanguage.SYSTEM
    }
