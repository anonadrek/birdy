package se.birdy.app.ui.components

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.TextOnCreme
import se.birdy.app.ui.theme.rememberDmSerifDisplay

/**
 * Field Journal headline (1.3.0): upright DM Serif Display, with `*word*` accent segments set
 * in DM Serif Display *italic* in [accentColor]. (1.2 used rotated Caveat for the accent;
 * handwriting now lives only in marginalia and sub-lines — spec 2026-09-24 §4.2.)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JournalHeadline(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 30.sp,
    plainColor: Color = TextOnCreme,
    accentColor: Color = AccentCopper,
) {
    val segments = parseJournalHeadline(text)
    val serif = rememberDmSerifDisplay()

    FlowRow(modifier = modifier.semantics(mergeDescendants = true) {}) {
        segments.forEach { seg ->
            Text(
                text = seg.text,
                color = if (seg is HeadlineSegment.Accent) accentColor else plainColor,
                fontFamily = serif,
                fontStyle = if (seg is HeadlineSegment.Accent) FontStyle.Italic else FontStyle.Normal,
                fontWeight = FontWeight.Normal,
                fontSize = fontSize,
                letterSpacing = (-0.01).em,
            )
        }
    }
}
