package se.birdy.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import se.birdy.app.ui.theme.AccentCopper

/**
 * Field Journal kicker (1.3.0): a short rule followed by Inter caps — "── IDENTIFIERA".
 * Sits above every screen headline. [color] defaults to rust on paper; pass
 * AccentCopperLight on dark moss / photos.
 *
 * T12d Important 1: [autoSize] is `null` by default, which keeps rendering exactly as before
 * (a fixed-size [Text], pixel-identical to every existing caller/screenshot) — pass a
 * [TextAutoSize] only where the label sits in a width-constrained cell that can otherwise force
 * a mid-word break at large font scales (e.g. the totals label in
 * `se.birdy.app.ui.stats.SeasonStatsScreen`'s private `TotalCell`).
 */
@Composable
fun MicroLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = AccentCopper,
    showRule: Boolean = true,
    autoSize: TextAutoSize? = null,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (showRule) {
            Box(Modifier.width(18.dp).height(1.dp).background(color))
            Spacer(Modifier.width(8.dp))
        }
        if (autoSize != null) {
            BasicText(
                text = text.uppercase(),
                style =
                    TextStyle(
                        color = color,
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 9.5.sp,
                        lineHeight = 12.sp,
                        fontWeight = FontWeight.W600,
                        letterSpacing = 0.16.em,
                    ),
                maxLines = 1,
                autoSize = autoSize,
            )
        } else {
            Text(
                text = text.uppercase(),
                color = color,
                fontFamily = FontFamily.SansSerif,
                fontSize = 9.5.sp,
                // Explicit, tight line height — otherwise this inherits the theme's bodyLarge
                // 22sp line height, which is why callers that budget a fixed-height row around
                // this text (e.g. BottomNavBar's TabCell) can overflow their cell.
                lineHeight = 12.sp,
                fontWeight = FontWeight.W600,
                letterSpacing = 0.16.em,
            )
        }
    }
}
