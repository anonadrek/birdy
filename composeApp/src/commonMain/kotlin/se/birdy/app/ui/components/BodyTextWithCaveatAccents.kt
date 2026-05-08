package se.birdy.app.ui.components

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.TextOnCreme
import se.birdy.app.ui.theme.rememberCaveat

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BodyTextWithCaveatAccents(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 13.sp,
    plainColor: Color = TextOnCreme,
    accentColor: Color = AccentCopper,
) {
    val segments = parseJournalHeadline(text)
    val caveat = rememberCaveat()
    FlowRow(modifier = modifier) {
        segments.forEach { seg ->
            when (seg) {
                is HeadlineSegment.Plain ->
                    Text(
                        text = seg.text,
                        color = plainColor,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Normal,
                        fontSize = fontSize,
                        lineHeight = fontSize.times(1.55f),
                    )
                is HeadlineSegment.Accent ->
                    Text(
                        text = seg.text,
                        color = accentColor,
                        fontFamily = caveat,
                        fontWeight = FontWeight.Bold,
                        fontSize = fontSize.times(1.2f),
                    )
            }
        }
    }
}
