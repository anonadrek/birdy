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
 * It also returns `""` for pipeline "no data" texts (see `SpeciesTextNoData.kt`) so the UI shows
 * its own localized empty-state string instead, and `SqlDelightSpeciesRepository.pickText` falls
 * back to the English text when the localized one cleans to blank.
 *
 * Implemented with plain string scanning (no regex) so the same logic behaves identically on the
 * JVM and Kotlin/Native (iOS) targets.
 */
internal fun cleanSpeciesText(raw: String): String {
    val normalized = raw.replace("\r\n", "\n").replace("\r", "\n")
    val lines = normalized.split("\n").toMutableList()

    val headingIsNoData = lines.isNotEmpty() && isHeadingLine(lines[0]) && isNoDataHeadingLine(lines[0].trim())
    if (!headingIsNoData && lines.isNotEmpty() && isHeadingLine(lines[0])) {
        lines.removeAt(0)
        while (lines.isNotEmpty() && lines[0].isBlank()) {
            lines.removeAt(0)
        }
    }

    val body = if (headingIsNoData) "" else dropHorizontalRules(lines).joinToString("\n")
    val paragraphs = if (headingIsNoData || isNoDataText(body)) emptyList() else keptParagraphs(body)
    if (paragraphs.isEmpty()) return ""

    val withoutBold = paragraphs.joinToString("\n\n").replace(BOLD_MARKER, "")
    return withoutBold.split("\n").joinToString("\n") { line -> stripStarEmphasis(line) }.trim()
}

/**
 * [body] split into paragraphs, with a trailing sentinel or labelled meta-commentary paragraph
 * dropped (T11f / Important I1) — see [isTrailingNoDataParagraph].
 */
private fun keptParagraphs(body: String): List<String> {
    val paragraphs = splitIntoParagraphs(body).toMutableList()
    while (paragraphs.isNotEmpty() && isTrailingNoDataParagraph(paragraphs.last())) {
        paragraphs.removeAt(paragraphs.lastIndex)
    }
    return paragraphs
}

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
internal const val BOLD_MARKER = "**"
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

/** Splits [body] on blank lines into non-blank, possibly multi-line paragraphs. */
private fun splitIntoParagraphs(body: String): List<String> {
    val paragraphs = mutableListOf<String>()
    val current = StringBuilder()
    for (line in body.split("\n")) {
        if (line.isBlank()) {
            if (current.isNotEmpty()) {
                paragraphs.add(current.toString())
                current.clear()
            }
        } else {
            if (current.isNotEmpty()) current.append("\n")
            current.append(line)
        }
    }
    if (current.isNotEmpty()) paragraphs.add(current.toString())
    return paragraphs
}
