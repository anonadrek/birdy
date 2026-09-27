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
fun possessive(
    name: String,
    suffix: String,
    sibilantSuffix: String,
    sibilantEndings: String,
): String {
    val trimmed = name.trim()
    val lastChar = trimmed.lastOrNull() ?: return trimmed + suffix
    return trimmed + if (sibilantEndings.contains(lastChar, ignoreCase = true)) sibilantSuffix else suffix
}
