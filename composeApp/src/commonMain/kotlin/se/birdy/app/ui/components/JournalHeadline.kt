package se.birdy.app.ui.components

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.TextOnCreme
import se.birdy.app.ui.theme.rememberCaveat
import se.birdy.app.ui.theme.rememberDmSerifDisplay

/**
 * Field Journal headline: DM Serif Display Italic plain-text + Caveat Bold
 * accent words (rotated -3°, copper).
 *
 * Use `*word*` syntax to mark an accent segment. Long headlines wrap via FlowRow
 * but rotation may look odd across line breaks — keep headlines short (<20 chars).
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
    val caveat = rememberCaveat()

    FlowRow(modifier = modifier.semantics(mergeDescendants = true) {}) {
        segments.forEach { seg ->
            when (seg) {
                is HeadlineSegment.Plain ->
                    Text(
                        text = seg.text,
                        color = plainColor,
                        fontFamily = serif,
                        fontStyle = FontStyle.Italic,
                        fontWeight = FontWeight.Normal,
                        fontSize = fontSize,
                    )
                is HeadlineSegment.Accent ->
                    Text(
                        text = seg.text,
                        color = accentColor,
                        fontFamily = caveat,
                        fontStyle = FontStyle.Normal,
                        fontWeight = FontWeight.Bold,
                        fontSize = fontSize.times(1.15f),
                        modifier = Modifier.rotate(-3f),
                    )
            }
        }
    }
}
