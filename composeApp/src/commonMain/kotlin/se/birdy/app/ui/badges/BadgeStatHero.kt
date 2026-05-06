package se.birdy.app.ui.badges

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.badges_label_badges
import birdy_bird_scanner.composeapp.generated.resources.badges_label_monthly_streak
import birdy_bird_scanner.composeapp.generated.resources.badges_label_species_seen
import birdy_bird_scanner.composeapp.generated.resources.badges_label_weekly_streak
import birdy_bird_scanner.composeapp.generated.resources.badges_progress_format
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.HeroMossLight
import se.birdy.app.ui.theme.HeroMossMid
import se.birdy.app.ui.theme.LabelOnHero
import se.birdy.app.ui.theme.TextOnHero

@Composable
fun BadgeStatHero(
    seenSpecies: Int,
    totalSpecies: Int,
    unlockedCount: Int,
    totalBadges: Int,
    weeklyStreak: Int?,
    monthlyStreak: Int?,
    modifier: Modifier = Modifier,
) {
    val gradient =
        Brush.verticalGradient(
            colors = listOf(HeroMossLight, HeroMossMid),
        )

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(gradient)
                .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 22.dp),
    ) {
        Text(
            text = stringResource(Res.string.badges_label_species_seen).uppercase(),
            color = LabelOnHero,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.5.sp,
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "$seenSpecies",
                color = TextOnHero,
                fontSize = 42.sp,
                style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Normal),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "/ $totalSpecies",
                color = LabelOnHero,
                fontSize = 14.sp,
            )
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { if (totalSpecies > 0) (seenSpecies.toFloat() / totalSpecies).coerceAtMost(1f) else 0f },
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
            color = AccentCopper,
            trackColor = TextOnHero.copy(alpha = 0.2f),
        )
        Spacer(Modifier.height(10.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            HeroPill(
                value = stringResource(Res.string.badges_progress_format, unlockedCount, totalBadges),
                label = stringResource(Res.string.badges_label_badges),
                modifier = Modifier.weight(1f),
            )
            if (weeklyStreak != null) {
                HeroPill(
                    value = "${weeklyStreak}v",
                    label = stringResource(Res.string.badges_label_weekly_streak),
                    modifier = Modifier.weight(1f),
                )
            }
            if (monthlyStreak != null) {
                HeroPill(
                    value = "${monthlyStreak}m",
                    label = stringResource(Res.string.badges_label_monthly_streak),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun HeroPill(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            modifier
                .background(TextOnHero.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                .padding(vertical = 8.dp, horizontal = 6.dp),
    ) {
        Text(value, color = TextOnHero, fontSize = 18.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            label.uppercase(),
            color = LabelOnHero,
            fontSize = 8.sp,
            letterSpacing = 0.8.sp,
        )
    }
}
