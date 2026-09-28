package se.birdy.content

// "No data"/meta-commentary detection for [cleanSpeciesText], split out of SpeciesTextCleaner.kt
// (like SpeciesTextEmphasis.kt was in T11) to keep each file within detekt's TooManyFunctions
// threshold.

/**
 * The sentences the content pipeline writes when Wikipedia has no migration data for a species.
 * They are pipeline artefacts, not content: the LLM often appended meta-commentary about its
 * source after them ("The provided source text contains…", "**Förklaring:** Källtexten…"), so a
 * text that OPENS with one is treated as empty and the UI shows its own localized "no migration
 * data" string. Deliberately narrow (exact sentences); the same rule also fires on a heading made
 * of just the sentinel text ([isNoDataHeadingLine]) and on a matching final paragraph
 * ([isTrailingNoDataParagraph], T11f) — not only at the very start of the text. Texts with
 * meta-commentary left in the MIDDLE of a paragraph are not caught by this rule and are waiting
 * for a manual content review pass.
 */
private val NO_DATA_SENTINELS =
    listOf(
        "Migration data unavailable for this species.",
        "Migrationsdata saknas för denna art.",
    )

/**
 * Openings of LLM meta-commentary about the pipeline's source text instead of species content
 * (e.g. "The source text provides no information…", "Källtexten innehåller ingen information…").
 * A text that opens with one of these is treated as empty, for the same reason and with the same
 * narrow scope as [NO_DATA_SENTINELS]. Matched case-insensitively on the first letter only.
 */
private val SOURCE_META_OPENINGS =
    listOf(
        "The source text",
        "The provided source text",
        "The Wikipedia source text",
        "Källtexten",
    )

/**
 * Language labels the pipeline sometimes put in front of a sentinel (`sv: "Migrationsdata…"`,
 * `En: Migration data…`). Only stripped for the [isNoDataText] check, never from real prose.
 */
private val LANGUAGE_LABEL_PREFIXES = listOf("sv:", "en:")

private const val OPENING_QUOTES = "\"“”„"

internal fun isNoDataText(body: String): Boolean {
    val opening = openingForNoDataCheck(body)
    return NO_DATA_SENTINELS.any { startsWithFirstLetterIgnoringCase(opening, it) } ||
        SOURCE_META_OPENINGS.any { startsWithFirstLetterIgnoringCase(opening, it) }
}

/** The start of [body] with bold markers, one language label and an opening quote removed. */
private fun openingForNoDataCheck(body: String): String {
    var opening = body.replace(BOLD_MARKER, "").trimStart()
    val label = LANGUAGE_LABEL_PREFIXES.firstOrNull { opening.startsWith(it, ignoreCase = true) }
    if (label != null) opening = opening.substring(label.length).trimStart()
    if (opening.isNotEmpty() && opening[0] in OPENING_QUOTES) opening = opening.substring(1)
    return opening
}

private fun startsWithFirstLetterIgnoringCase(
    text: String,
    prefix: String,
): Boolean =
    text.length >= prefix.length &&
        text[0].equals(prefix[0], ignoreCase = true) &&
        text.startsWith(prefix.substring(1), startIndex = 1)

/**
 * The heading text with its `#`/`**` wrapping removed, e.g. `"# Migration Data Unavailable"` →
 * `"Migration Data Unavailable"`.
 */
private fun headingInnerText(trimmed: String): String =
    when {
        trimmed.startsWith("#") -> trimmed.substring(trimmed.takeWhile { it == '#' }.length).trim()
        trimmed.startsWith(BOLD_MARKER) && trimmed.endsWith(BOLD_MARKER) ->
            trimmed.substring(BOLD_MARKER.length, trimmed.length - BOLD_MARKER.length).trim()
        else -> trimmed
    }

/**
 * `line` is already known to be a heading line. True when its text (case-insensitively, and
 * ignoring a trailing period) IS one of the [NO_DATA_SENTINELS] or opens with a
 * [SOURCE_META_OPENINGS] phrase, e.g. `# Migration Data Unavailable for This Species`. A markdown
 * heading capitalises every word, so [isNoDataText]'s exact (first-letter-only-case-insensitive)
 * match never fires on it; whatever prose follows such a heading is pipeline meta-commentary, not
 * species content, so the whole text collapses to blank rather than just dropping the heading
 * line (T11f / Minor C3).
 */
internal fun isNoDataHeadingLine(line: String): Boolean {
    val normalized = headingInnerText(line).trimEnd('.', ' ').lowercase()
    if (normalized.isEmpty()) return false
    return NO_DATA_SENTINELS.any { it.trimEnd('.').lowercase() == normalized } ||
        SOURCE_META_OPENINGS.any { normalized.startsWith(it.lowercase()) }
}

/** Labels the pipeline sometimes puts in front of a trailing meta-commentary paragraph. */
private val TRAILING_META_LABELS = listOf("Note:", "Notering:", "Obs:", "Förklaring:")

/**
 * Removes every `**bold**` marker in [paragraph] (the pipeline sometimes only wraps the LABEL,
 * e.g. `**Notering:** Källtexten…`), then strips a remaining single-`*` wrapper around the WHOLE
 * trimmed result, if any (the pipeline sometimes wraps the entire meta-commentary paragraph in
 * emphasis instead, e.g. `*Notering: Källtexten…*`). Leaves the text alone otherwise — this is
 * only used to detect a no-data paragraph, never to change what gets kept, so it is safe even for
 * prose that starts and ends with an unrelated `*`.
 */
private fun unwrapParagraphEmphasis(paragraph: String): String {
    val withoutBold = paragraph.replace(BOLD_MARKER, "").trim()
    val starWrapped = withoutBold.length >= 2 && withoutBold.startsWith("*") && withoutBold.endsWith("*")
    return if (starWrapped) withoutBold.substring(1, withoutBold.length - 1) else withoutBold
}

/**
 * True when [paragraph] (one blank-line-delimited block, possibly several lines) is itself a
 * [NO_DATA_SENTINELS]/[SOURCE_META_OPENINGS] match, or is a [TRAILING_META_LABELS]-prefixed
 * wrapper around one, once any [unwrapParagraphEmphasis] wrapping is removed. Used to drop a
 * sentinel or a labelled meta-commentary paragraph left at the END of an otherwise real text
 * (T11f / Important I1) — the mirror of [isNoDataText], which only looks at the opening of the
 * whole text.
 */
internal fun isTrailingNoDataParagraph(paragraph: String): Boolean {
    val unwrapped = unwrapParagraphEmphasis(paragraph)
    if (isNoDataText(unwrapped)) return true
    val withoutLabelWrap = unwrapped.trimStart()
    val label = TRAILING_META_LABELS.firstOrNull { withoutLabelWrap.startsWith(it, ignoreCase = true) }
    return label != null && isNoDataText(withoutLabelWrap.substring(label.length).trimStart())
}
