package se.birdy.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import se.birdy.app.ui.theme.CardPaper
import se.birdy.app.ui.theme.Hairline
import se.birdy.app.ui.theme.InkMuted
import se.birdy.app.ui.theme.rememberCaveat

/**
 * Naturalist plate-frame: card-paper rounded box with hairline border, photo
 * centered + Caveat-italic caption below (`Pl. {idx} · {name}, in nature`).
 * Used in Species Profile + Observation Detail in place of LargeTopAppBar.
 */
@Composable
fun PlateFrame(
    plateLabel: String,
    captionLine: String,
    modifier: Modifier = Modifier,
    image: @Composable () -> Unit,
) {
    val caveat = rememberCaveat()
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .semantics(mergeDescendants = true) {}
                .clip(RoundedCornerShape(16.dp))
                .background(CardPaper)
                .border(width = 1.dp, color = Hairline, shape = RoundedCornerShape(16.dp))
                .padding(8.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            image()
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (plateLabel.isNotEmpty()) "Pl. $plateLabel · $captionLine" else captionLine,
            color = InkMuted,
            fontFamily = caveat,
            fontWeight = FontWeight.Normal,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}
