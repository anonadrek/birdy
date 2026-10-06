package se.birdy.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

/**
 * [text] with its `*accent*` markup (see [parseJournalHeadline]) applied as [accentColor] spans,
 * for a single Text in one font. The asterisks never reach the screen (Release 1.3.0 Plan 3
 * Task 7: Listen showed "Håll telefonen stilla och *låt den sjunga*").
 */
fun journalAccentAnnotated(
    text: String,
    accentColor: Color,
): AnnotatedString =
    buildAnnotatedString {
        parseJournalHeadline(text).forEach { segment ->
            when (segment) {
                is HeadlineSegment.Plain -> append(segment.text)
                is HeadlineSegment.Accent -> withStyle(SpanStyle(color = accentColor)) { append(segment.text) }
            }
        }
    }

/** [text] without its `*accent*` markup, for places that show it in a single style. */
fun journalPlainText(text: String): String = parseJournalHeadline(text).joinToString(separator = "") { it.text }
