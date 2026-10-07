package se.birdy.app.ui.credits

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink

/**
 * [template] (a string resource with `%1$s`, `%2$s`... placeholders) with each placeholder
 * replaced by [parts] in order of their number, the parts with a url as links. Building the text
 * this way keeps each language's word order in its own string resource while the links still land
 * on the right words. [linkStyles] null leaves the links out entirely (a credit inside a button,
 * where a link would steal the button's tap), so the text is the same either way.
 */
internal fun linkedTemplate(
    template: String,
    parts: List<CreditPart>,
    linkStyles: TextLinkStyles?,
): AnnotatedString =
    buildAnnotatedString {
        var cursor = 0
        for (match in PLACEHOLDER.findAll(template)) {
            append(template.substring(cursor, match.range.first))
            val part = parts.getOrNull(match.groupValues[1].toInt() - 1)
            if (part != null) appendPart(part, linkStyles) else append(match.value)
            cursor = match.range.last + 1
        }
        append(template.substring(cursor))
    }

internal fun AnnotatedString.Builder.appendPart(
    part: CreditPart,
    linkStyles: TextLinkStyles?,
) {
    if (part.url != null && linkStyles != null) {
        withLink(LinkAnnotation.Url(part.url, linkStyles)) { append(part.text) }
    } else {
        append(part.text)
    }
}

/**
 * This piece with no-break spaces, so a line never ends inside a licence name or "Wikimedia
 * Commons" ("CC BY" at the end of one line and "2.0" on the next).
 */
internal fun String.keepTogether(): String = replace(' ', NO_BREAK_SPACE)

internal const val NO_BREAK_SPACE = '\u00A0'

// "%1$s": a percent sign, the placeholder's number, then "$s".
private val PLACEHOLDER = Regex("%(\\d)\\\$s")
