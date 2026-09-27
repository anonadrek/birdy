package se.birdy.content

/**
 * The species content YAML (built by the content pipeline) writes description/migration/
 * marginalia text as lightweight markdown: a leading `# Species name` or `**Species name**`
 * heading line (redundant with the profile screen's own hero title), single-`*` emphasis around
 * scientific names (`*Circaetus gallicus*`), `**bold**` labels such as `**Förklaring:**`, and
 * the occasional `---` horizontal rule. The app renders this as plain text (T11 review, release
 * 1.3.0): without cleaning, `SpeciesProfileScreen`'s drop-cap paragraph shows a literal "#" as
 * the (44sp, copper) first letter, and the migration section opens with a visible "# …" line.
 * This function strips that pipeline-authoring markdown down to plain prose.
 *
 * It also returns `""` for pipeline "no data" texts (see [NO_DATA_SENTINELS] and
 * [SOURCE_META_OPENINGS]) so the UI shows its own localized empty-state string instead, and
 * `SqlDelightSpeciesRepository.pickText` falls back to the English text when the localized
 * one cleans to blank.
 *
 * Implemented with plain string scanning (no regex) so the same logic behaves identically on the
 * JVM and Kotlin/Native (iOS) targets.
 */
internal fun cleanSpeciesText(raw: String): String {
    val normalized = raw.replace("\r\n", "\n").replace("\r", "\n")
    val lines = normalized.split("\n").toMutableList()

    if (lines.isNotEmpty() && isHeadingLine(lines[0])) {
        lines.removeAt(0)
        while (lines.isNotEmpty() && lines[0].isBlank()) {
            lines.removeAt(0)
        }
    }

    val body = dropHorizontalRules(lines).joinToString("\n")
    if (isNoDataText(body)) return ""

    val withoutBold = body.replace(BOLD_MARKER, "")
    val cleaned = withoutBold.split("\n").joinToString("\n") { line -> stripStarEmphasis(line) }

    return cleaned.trim()
}

/**
 * The sentences the content pipeline writes when Wikipedia has no migration data for a species.
 * They are pipeline artefacts, not content: the LLM often appended meta-commentary about its
 * source after them ("The provided source text contains…", "**Förklaring:** Källtexten…"), so a
 * text that OPENS with one is treated as empty and the UI shows its own localized "no migration
 * data" string. Deliberately narrow (exact sentences, opening position only); cleaning up the
 * content itself is tracked separately in the content pipeline.
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

private fun isNoDataText(body: String): Boolean {
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

private const val HORIZONTAL_RULE_MARKS = "-*_"
private const val HORIZONTAL_RULE_MIN_MARKS = 3

/**
 * A markdown thematic break: a line made only of three or more of the same `-`, `*` or `_`,
 * optionally separated by spaces (`---`, `* * *`, `_____`).
 */
private fun isHorizontalRule(line: String): Boolean {
    val trimmed = line.trim()
    val mark = trimmed.firstOrNull()
    return mark != null &&
        mark in HORIZONTAL_RULE_MARKS &&
        trimmed.all { it == mark || it == ' ' || it == '\t' } &&
        trimmed.count { it == mark } >= HORIZONTAL_RULE_MIN_MARKS
}

/**
 * Drops [isHorizontalRule] lines together with the blank lines that followed them when the
 * preceding kept line was already blank, so `A\n\n---\n\nB` becomes `A\n\nB` (one paragraph break,
 * not two).
 */
private fun dropHorizontalRules(lines: List<String>): List<String> {
    val kept = ArrayList<String>(lines.size)
    var skipBlanks = false
    for (line in lines) {
        when {
            isHorizontalRule(line) -> skipBlanks = kept.isEmpty() || kept.last().isBlank()
            skipBlanks && line.isBlank() -> Unit
            else -> {
                kept.add(line)
                skipBlanks = false
            }
        }
    }
    return kept
}

private const val ATX_HEADING_MAX_HASHES = 6
private const val BOLD_HEADING_MIN_LENGTH = 5
private const val BOLD_MARKER = "**"
private const val SENTENCE_END_MARKS = ".!?"

/**
 * A leading heading line: markdown ATX (`#` through `######`, followed by a space or end of
 * line) or a line that is entirely ONE `**bold**` span that does not end a sentence. Only ever
 * checked against the FIRST line of a text; inline `**bold**`/`*italic*` elsewhere is handled
 * separately.
 */
private fun isHeadingLine(line: String): Boolean {
    val trimmed = line.trim()
    return trimmed.isNotEmpty() && (isAtxHeading(trimmed) || isBoldOnlyHeading(trimmed))
}

private fun isAtxHeading(trimmed: String): Boolean {
    if (trimmed[0] != '#') return false
    val hashes = trimmed.takeWhile { it == '#' }.length
    val followedBySpaceOrEnd = hashes == trimmed.length || trimmed[hashes] == ' '
    return hashes in 1..ATX_HEADING_MAX_HASHES && followedBySpaceOrEnd
}

/**
 * `**Grågam - Förekomst i Norden**` is a heading; `**Talgoxen** är en **vanlig** fågel` (several
 * spans) and `**Talgoxen är en vanlig fågel.**` (a whole bold sentence) are prose and are kept.
 */
private fun isBoldOnlyHeading(trimmed: String): Boolean {
    val wrappedInBold =
        trimmed.length >= BOLD_HEADING_MIN_LENGTH &&
            trimmed.startsWith(BOLD_MARKER) &&
            trimmed.endsWith(BOLD_MARKER)
    if (!wrappedInBold) return false
    val inner = trimmed.substring(BOLD_MARKER.length, trimmed.length - BOLD_MARKER.length).trim()
    return inner.isNotEmpty() && BOLD_MARKER !in inner && inner.last() !in SENTENCE_END_MARKS
}
