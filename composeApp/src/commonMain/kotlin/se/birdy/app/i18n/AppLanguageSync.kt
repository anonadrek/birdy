package se.birdy.app.i18n

import kotlinx.coroutines.flow.first
import se.birdy.datastore.AppLanguage
import se.birdy.datastore.UserPreferences

/**
 * The app language, with the platform's per-app language as the truth where there is one.
 *
 * On Android 13+ the language can be changed outside Birdy (system Settings, Apps, Birdy, Language,
 * offered because the manifest has a localeConfig). The UI follows it at once, but the saved
 * preference did not, so species names, notifications and the PDF stayed in the old language
 * (2026-10-07: "Råka" on an English screen). [appliedTags] is `LocaleManager.applicationLocales`
 * as `toLanguageTags()` there, or null where the platform has no per-app language (Android below
 * 13, iOS), and then the preference stays the truth. The preference is written only when it
 * differs, so Settings shows the same choice as the system.
 */
suspend fun reconcileAppLanguage(
    prefs: UserPreferences,
    appliedTags: String?,
): AppLanguage {
    val stored = prefs.appLanguage.first()
    val language = appliedTags?.let(::appLanguageFromLocaleTags) ?: stored
    if (language != stored) prefs.setAppLanguage(language)
    return language
}
