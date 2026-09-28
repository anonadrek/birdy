package se.birdy.app.ui.badges

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import se.birdy.app.ui.components.StampSeal
import se.birdy.app.ui.components.StampSealState
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.MarginaliaInk
import se.birdy.app.ui.theme.rememberCaveat
import se.birdy.content.Locale

// Already over threshold pre-1.3.0 (baselined); adding accentColor changes the baseline's
// exact signature match, so this is a fresh, justified suppress rather than a baseline edit
// (house rule: never extend detekt-baseline.xml).
@Suppress("LongParameterList")
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
    accentColor: Color = AccentCopper,
) {
    val caveat = rememberCaveat()
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StampSeal(
            state = StampSealState.Unlocked(number = stampNumber, glyph = glyph, name = localizedName),
            accentColor = accentColor,
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
