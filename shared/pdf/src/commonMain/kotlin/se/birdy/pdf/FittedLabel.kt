package se.birdy.pdf

/** A label fitted into a column: the text to draw and its font scale (1 = the metric's own size). */
internal data class FittedLabel(
    val text: String,
    val scale: Float,
)

internal const val ELLIPSIS = "…"

/**
 * Fits [text] into [maxWidth] (pt), the same way on Android and iOS: the whole text at its own size when it fits;
 * otherwise the whole text shrunk, but never below [minScale]; and only when even that is too wide, the longest start
 * of the text that fits at [minScale], ended with an ellipsis (trailing spaces trimmed first). [measure] gives a
 * string's width at scale 1 in the platform's font (Paint.measureText, NSString.sizeWithAttributes); a font's widths
 * scale linearly with its size. Found 2026-10-08: the English export's "Great Spotted Woodpecker" ran into its bar in
 * the top-species chart, whose labels have [JournalPdfMetrics.TOPS_BAR_OFFSET] pt before the bars.
 */
internal fun fitLabel(
    text: String,
    maxWidth: Float,
    minScale: Float,
    measure: (String) -> Float,
): FittedLabel {
    val width = measure(text)
    return when {
        width <= maxWidth -> FittedLabel(text, 1f)
        width * minScale <= maxWidth -> FittedLabel(text, maxWidth / width)
        else -> FittedLabel(cutToFit(text, maxWidth / minScale, measure), minScale)
    }
}

/** The longest start of [text] that, with the ellipsis, measures at most [budget]; just the ellipsis if none does. */
private fun cutToFit(
    text: String,
    budget: Float,
    measure: (String) -> Float,
): String {
    var end = text.length - 1
    while (end > 0 && measure(text.take(end).trimEnd() + ELLIPSIS) > budget) end--
    return if (end > 0) text.take(end).trimEnd() + ELLIPSIS else ELLIPSIS
}
