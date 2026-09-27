package se.birdy.app.ui.diary

/**
 * Forms a genitive/possessive display name — "Albin" -> "Albins" (Swedish) / "Albin's" (English)
 * — without hardcoding either language's grammar rules in the caller.
 *
 * [name] is trimmed first (a name typed with trailing whitespace shouldn't leak a stray space
 * before the suffix). If the trimmed name's last character (case-insensitively) is one of
 * [sibilantEndings], [sibilantSuffix] is appended instead of [suffix] — e.g. Swedish names
 * ending in s/x/z take no extra "s" ("Lars", not "Larss"), English names ending in "s" take
 * only an apostrophe ("James'", not "James's").
 */
internal fun possessive(
    name: String,
    suffix: String,
    sibilantSuffix: String,
    sibilantEndings: String,
): String {
    val trimmed = name.trim()
    val lastChar = trimmed.lastOrNull() ?: return trimmed + suffix
    return trimmed + if (sibilantEndings.contains(lastChar, ignoreCase = true)) sibilantSuffix else suffix
}

/**
 * The literal fallback WORD [OnboardingViewModel][se.birdy.app.ui.onboarding.OnboardingViewModel]
 * persisted as `userName` when a user skipped the name field, pre-fix — `onboarding_p3_fallback_name`
 * is "Min" (Swedish) / "My" (English). `complete()` did `trimmed.ifEmpty { defaultFallbackName }`,
 * so instead of leaving `userName` blank it wrote this word in as if it were a real name.
 */
private val LEGACY_ONBOARDING_FALLBACK_NAMES = setOf("Min", "My")

/**
 * Normalizes a stored `userName` for display: `null` when it's blank, or — after trimming —
 * exactly equal (case-sensitively) to one of [LEGACY_ONBOARDING_FALLBACK_NAMES]; otherwise the
 * trimmed name. Every reader of `userName` must go through this, not just `.isEmpty()`/`.ifEmpty`,
 * because [LEGACY_ONBOARDING_FALLBACK_NAMES] are real, already-persisted values for existing
 * production users, not just a hypothetical input — this fixes it on read rather than migrating
 * stored data or touching onboarding's own persistence.
 */
internal fun displayNameOrNull(stored: String): String? {
    val trimmed = stored.trim()
    return trimmed.takeUnless { it.isEmpty() || it in LEGACY_ONBOARDING_FALLBACK_NAMES }
}
