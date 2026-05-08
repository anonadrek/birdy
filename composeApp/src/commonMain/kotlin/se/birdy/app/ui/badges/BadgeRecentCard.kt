package se.birdy.app.ui.badges

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import se.birdy.app.ui.components.StampSeal
import se.birdy.app.ui.components.StampSealState
import se.birdy.app.ui.theme.MarginaliaInk
import se.birdy.app.ui.theme.rememberCaveat
import se.birdy.content.Locale

@Composable
fun BadgeRecentCard(
    localizedName: String,
    stampNumber: Int,
    glyph: String?,
    unlockedAt: Instant,
    now: Instant,
    locale: Locale,
    zone: TimeZone,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val caveat = rememberCaveat()
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StampSeal(
            state = StampSealState.Unlocked(number = stampNumber, glyph = glyph, name = localizedName),
            onClick = onClick,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = formatRelativeBadgeDate(unlockedAt, now, zone, locale),
            color = MarginaliaInk,
            fontFamily = caveat,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
        )
    }
}
