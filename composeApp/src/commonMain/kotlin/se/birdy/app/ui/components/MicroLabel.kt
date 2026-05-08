package se.birdy.app.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import se.birdy.app.ui.theme.AccentCopper

/**
 * Field Journal micro-label: "DISCOVERIES · NO 12" — Inter caps, copper, 0.28em.
 * Renders at top of every screen-intro before the headline.
 */
@Composable
fun MicroLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        color = AccentCopper,
        fontFamily = FontFamily.SansSerif,
        fontSize = 9.sp,
        fontWeight = FontWeight.W700,
        letterSpacing = 0.28.em,
    )
}
