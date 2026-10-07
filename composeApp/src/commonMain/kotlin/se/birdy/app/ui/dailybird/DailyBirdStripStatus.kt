package se.birdy.app.ui.dailybird

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

/** The status line of [DailyBirdStrip]: a seal once caught, then "Inte fångad idag · 0 av 3 dagar". */
@Composable
internal fun DailyBirdStripStatus(
    challenge: DailyBirdChallenge,
    status: String,
    caveat: FontFamily,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (challenge.caughtToday) {
            DailyBirdSeals(filled = 1, total = 1, sealSize = 13.dp, colors = PaperSealColors)
            Spacer(Modifier.width(5.dp))
        }
        Text(text = status, color = MarginaliaInk, fontFamily = caveat, fontSize = 15.sp, lineHeight = 18.sp)
    }
}
