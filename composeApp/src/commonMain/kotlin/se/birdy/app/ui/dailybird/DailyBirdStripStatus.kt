package se.birdy.app.ui.dailybird

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import se.birdy.app.dailybird.DailyBirdChallenge
import se.birdy.app.ui.theme.MarginaliaInk

/**
 * The status line of [DailyBirdStrip]: a seal once caught, "Inte fångad idag · 0 av 3 dagar", and
 * for a user without Premium the "Premium-märke" tag (Albin, 2026-10-07). A FlowRow: the tag sits
 * beside the status and wraps under it when the line is full (200 % text).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DailyBirdStripStatus(
    challenge: DailyBirdChallenge,
    status: String,
    caveat: FontFamily,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (challenge.caughtToday) {
                DailyBirdSeals(filled = 1, total = 1, sealSize = 13.dp, colors = PaperSealColors)
                Spacer(Modifier.width(5.dp))
            }
            Text(text = status, color = MarginaliaInk, fontFamily = caveat, fontSize = 15.sp, lineHeight = 18.sp)
        }
        if (challenge.showPremiumBadgeTag) DailyBirdPremiumBadgeTag()
    }
}
