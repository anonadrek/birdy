package se.birdy.app.ui.theme

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign

/**
 * Parses inline italic-mixed syntax: `Birdy *of* Sweden` → AnnotatedString where
 * `*of*` is rendered with [SpanStyle(fontStyle = Italic, color = accent)].
 *
 * Escape literal asterisks with `\*`. Unmatched single asterisk is left as-is.
 * Empty pairs `**` collapse to nothing.
 */
internal fun parseItalicMixed(
    input: String,
    accent: Color,
): AnnotatedString =
    AnnotatedString
        .Builder()
        .apply {
            var i = 0
            while (i < input.length) {
                val c = input[i]
                when {
                    c == '\\' && i + 1 < input.length && input[i + 1] == '*' -> {
                        append('*')
                        i += 2
                    }
                    c == '*' -> {
                        val end = input.indexOf('*', startIndex = i + 1)
                        if (end < 0) {
                            // Unmatched — treat as literal
                            append('*')
                            i += 1
                        } else if (end == i + 1) {
                            // Empty pair — drop both
                            i += 2
                        } else {
                            val segment = input.substring(i + 1, end)
                            val start = length
                            append(segment)
                            addStyle(
                                SpanStyle(fontStyle = FontStyle.Italic, color = accent),
                                start = start,
                                end = start + segment.length,
                            )
                            i = end + 1
                        }
                    }
                    else -> {
                        append(c)
                        i += 1
                    }
                }
            }
        }.toAnnotatedString()

@Composable
fun ItalicMixedText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = style.color,
    italicAccent: Color = AccentCopperLight,
    textAlign: TextAlign? = null,
) {
    val annotated = parseItalicMixed(text, italicAccent)
    BasicText(
        text = annotated,
        modifier = modifier,
        style = style.copy(color = color, textAlign = textAlign ?: style.textAlign),
    )
}
