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
 * The Swedish onboarding fallback word — `onboarding_p3_fallback_name` in the Swedish
 * strings.xml — persisted as `userName` by onboarding builds BEFORE 1.3.0, for anyone who
 * skipped the name field: [OnboardingViewModel][se.birdy.app.ui.onboarding.OnboardingViewModel]
 * used to do `trimmed.ifEmpty { defaultFallbackName }` instead of leaving `userName` blank. From
 * 1.3.0, `complete()` stores "" instead (no more fallback persisted), so this is now purely a
 * HISTORICAL value some already-installed users still have on disk, not something a fresh
 * install can produce — see [displayNameOrNull]'s KDoc for why it must still be masked.
 *
 * Unlike the English fallback word ("My" — see [displayNameOrNull]'s KDoc for why THAT one is
 * conditional), "Min" is masked unconditionally in both languages: it isn't a plausible real
 * Swedish (or English) given name, so there's no real-user tradeoff to weigh.
 */
internal const val HISTORICAL_SV_ONBOARDING_FALLBACK_NAME = "Min"

/**
 * Normalizes a stored `userName` for display: `null` when it's blank or — after trimming —
 * exactly equal (case-sensitively) to one of [maskedNames]; otherwise the trimmed name. Every
 * reader of `userName` must go through this, not just `.isEmpty()`/`.ifEmpty`, because a stored
 * name can still be a historical onboarding-fallback word — see
 * [HISTORICAL_SV_ONBOARDING_FALLBACK_NAME]'s KDoc — even though onboarding no longer writes one.
 *
 * [maskedNames] should always include [HISTORICAL_SV_ONBOARDING_FALLBACK_NAME] plus the CURRENT
 * UI language's onboarding fallback word (`stringResource(Res.string.onboarding_p3_fallback_name)`
 * where composable) — "Min" again in Swedish (a harmless duplicate) or "My" in English.
 *
 * "My" is deliberately NOT masked unconditionally, unlike this function's version before the
 * T8f / 1.3.0 change: it's a real, if uncommon, Swedish given name, and masking it everywhere
 * hid real Swedish users' names forever. It's only ever the onboarding fallback word in the
 * ENGLISH UI (`onboarding_p3_fallback_name` is "My" there, "Min" in Swedish) — so only there can
 * a stored "My" actually have come from a pre-1.3.0 skip-the-name-field flow and need masking.
 * In the Swedish UI a stored "My" can only be a real person's real name, and correctly gets its
 * possessive form ("Mys dagbok.") instead of being mistaken for the fallback.
 */
internal fun displayNameOrNull(
    stored: String,
    maskedNames: Set<String>,
): String? {
    val trimmed = stored.trim()
    return trimmed.takeUnless { it.isEmpty() || it in maskedNames }
}
