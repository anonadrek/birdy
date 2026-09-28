package se.birdy.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import se.birdy.app.ui.theme.AccentCopper

/**
 * Field Journal kicker (1.3.0): a short rule followed by Inter caps — "── IDENTIFIERA".
 * Sits above every screen headline. [color] defaults to rust on paper; pass
 * AccentCopperLight on dark moss / photos.
 *
 * T12e Minor 3: [fontSize] defaults to the original fixed 9.5sp, so a default call renders
 * exactly as before — pass a smaller size where a CALLER has already decided (by measuring, e.g.
 * `se.birdy.app.ui.stats.SeasonStatsScreen`'s `TotalsRow`) that this label needs to shrink to fit
 * a width-constrained cell. (T12d's per-instance `TextAutoSize` branch was removed here: deciding
 * per-[MicroLabel] let two labels that must match each other, like the two totals cells, end up
 * rendered at different sizes — see `TotalsRow`'s own doc comment.)
 */
@Composable
fun MicroLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = AccentCopper,
    showRule: Boolean = true,
    fontSize: TextUnit = 9.5.sp,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (showRule) {
            Box(Modifier.width(18.dp).height(1.dp).background(color))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text.uppercase(),
            color = color,
            fontFamily = FontFamily.SansSerif,
            fontSize = fontSize,
            // Explicit, tight line height — otherwise this inherits the theme's bodyLarge
            // 22sp line height, which is why callers that budget a fixed-height row around
            // this text (e.g. BottomNavBar's TabCell) can overflow their cell.
            lineHeight = 12.sp,
            fontWeight = FontWeight.W600,
            letterSpacing = 0.16.em,
        )
    }
}
