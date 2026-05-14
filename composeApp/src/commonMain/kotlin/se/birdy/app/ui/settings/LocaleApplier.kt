package se.birdy.app.ui.settings

/**
 * Applies a per-app locale override.
 *
 * [tag] is a BCP-47 language tag (e.g. "sv", "en"). An empty string clears
 * the override and restores the system locale.
 *
 * On Android this delegates to AppCompatDelegate.setApplicationLocales.
 * On other targets this is a no-op (placeholder for future platforms).
 */
expect fun applyLocale(tag: String)
