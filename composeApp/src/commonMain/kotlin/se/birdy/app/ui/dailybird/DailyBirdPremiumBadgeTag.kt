package se.birdy.app.ui.dailybird

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.daily_bird_premium_badge_tag
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.theme.BrassInk
import se.birdy.app.ui.theme.BrassLight

/**
 * "PREMIUM-MÄRKE" under the challenge row's day count for a user without Premium: Dagens
 * fågel-jägare is a Premium badge (Albin, 2026-10-07). The Premium teaser's corner-tag style:
 * brass fill and brass ink (5.4:1 on brass, more on this lighter brass), so it reads on the hero's
 * dark text area as on any photo.
 */
@Composable
internal fun DailyBirdPremiumBadgeTag(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(Res.string.daily_bird_premium_badge_tag).uppercase(),
        color = BrassInk,
        fontSize = 9.sp,
        fontWeight = FontWeight.W700,
        // Without it the theme's 22sp bodyLarge line height makes the tag look like a button.
        lineHeight = 12.sp,
        maxLines = 1,
        letterSpacing = 0.12.em,
        modifier =
            modifier
                .clip(RoundedCornerShape(4.dp))
                .background(BrassLight)
                .padding(horizontal = 6.dp, vertical = 3.dp),
    )
}
