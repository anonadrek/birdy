package se.birdy.app.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import se.birdy.app.ui.theme.MarginaliaInk
import se.birdy.app.ui.theme.rememberCaveat

/**
 * Caveat sub-line under JournalHeadline. Short handwritten phrase (≤1 sentence).
 */
@Composable
fun JournalSubLine(
    text: String,
    modifier: Modifier = Modifier,
) {
    val caveat = rememberCaveat()
    Text(
        text = text,
        modifier = modifier.semantics(mergeDescendants = true) {},
        color = MarginaliaInk,
        fontFamily = caveat,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
    )
}
