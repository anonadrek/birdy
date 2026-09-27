package se.birdy.content

// Single-`*` emphasis stripping for [cleanSpeciesText], split out of SpeciesTextCleaner.kt to
// keep each file within detekt's TooManyFunctions threshold.

private fun isWordChar(c: Char) = c.isLetterOrDigit() || c == '_'

/**
 * An opening `*` candidate at [index]: must not be preceded by a word character (checked via
 * [precedingChar], the last character already appended to the output, so a span that was just
 * unwrapped counts as the text it left behind) and must not be immediately followed by
 * whitespace or another `*`.
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
internal fun stripStarEmphasis(line: String): String {
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
