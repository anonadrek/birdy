package se.birdy.content

/**
 * The species content YAML (built by the content pipeline) writes description/migration/
 * marginalia text as lightweight markdown — a leading `# Species name` or `**Species name**`
 * heading line (redundant with the profile screen's own hero title), plus occasional `**bold**`
 * and `*italic*` spans around scientific names. The app renders this as plain text (T11 review,
 * release 1.3.0): without cleaning, [se.birdy.app.ui.profile.SpeciesProfileScreen]'s drop-cap
 * paragraph shows a literal "#" as the (44sp, copper) first letter, and the migration section
 * opens with a visible "# …" line. This function strips that pipeline-authoring markdown down to
 * plain prose. Implemented with plain string scanning (no regex lookbehind) so the same logic
 * behaves identically on the JVM and Kotlin/Native (iOS) targets.
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

    val withoutHeading = lines.joinToString("\n")
    val withoutBold = withoutHeading.replace("**", "")
    val cleaned = withoutBold.split("\n").joinToString("\n") { line -> stripStarEmphasis(line) }

    return cleaned.trim()
}

private const val ATX_HEADING_MAX_HASHES = 6
private const val BOLD_HEADING_MIN_LENGTH = 5

/**
 * A leading heading line: markdown ATX (`#` through `######`, followed by a space or end of
 * line) or a line that is entirely a `**bold**` span. Only ever checked against the FIRST line
 * of a text — inline `**bold**`/`*italic*` elsewhere is handled separately.
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

private fun isBoldOnlyHeading(trimmed: String) =
    trimmed.length >= BOLD_HEADING_MIN_LENGTH && trimmed.startsWith("**") && trimmed.endsWith("**")

private fun isWordChar(c: Char) = c.isLetterOrDigit() || c == '_'

/**
 * An opening `*` candidate at [index]: must not be preceded by a word character (checked via
 * [precedingChar], the last character already appended to the output — the line itself may have
 * been mutated upstream) and must not be immediately followed by whitespace or another `*`.
 */
private fun isValidOpener(
    line: String,
    index: Int,
    precedingChar: Char?,
): Boolean {
    val nextChar = line.getOrNull(index + 1)
    val precededByWord = precedingChar != null && isWordChar(precedingChar)
    val followedByBoundary = nextChar == null || nextChar.isWhitespace() || nextChar == '*'
    return !precededByWord && !followedByBoundary
}

/**
 * A closing `*` candidate at [index]: must not be preceded by whitespace and must not be
 * followed by a word character or `*`.
 */
private fun isValidCloser(
    line: String,
    index: Int,
): Boolean {
    val afterChar = line.getOrNull(index + 1)
    val precededByBoundary = line[index - 1].isWhitespace()
    val followedByWord = afterChar != null && (isWordChar(afterChar) || afterChar == '*')
    return !precededByBoundary && !followedByWord
}

private fun findCloserIndex(
    line: String,
    from: Int,
): Int {
    for (j in from until line.length) {
        if (line[j] == '*' && isValidCloser(line, j)) return j
    }
    return -1
}

/**
 * Removes single-`*` emphasis markers around a word/phrase (`*Circaetus gallicus*` ->
 * `Circaetus gallicus`) while leaving a lone, unpaired `*` (e.g. `5 * 3`) untouched. `**bold**`
 * markers must already be stripped before this runs (see [cleanSpeciesText]), so every `*` seen
 * here is a single marker candidate. Conservative pairing rules (no lookbehind, so this works
 * identically on Kotlin/Native): see [isValidOpener] and [isValidCloser].
 */
private fun stripStarEmphasis(line: String): String {
    if ('*' !in line) return line
    val sb = StringBuilder()
    var i = 0
    while (i < line.length) {
        val c = line[i]
        val closerIndex =
            if (c == '*' && isValidOpener(line, i, sb.lastOrNull())) {
                findCloserIndex(line, i + 1)
            } else {
                -1
            }
        if (closerIndex != -1) {
            sb.append(line.substring(i + 1, closerIndex))
            i = closerIndex + 1
        } else {
            sb.append(c)
            i++
        }
    }
    return sb.toString()
}
