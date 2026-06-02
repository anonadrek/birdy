package se.birdy.app.ui.badges

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.trophy_hero_empty_name
import birdy_bird_scanner.composeapp.generated.resources.trophy_hero_recent_label
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.components.MicroLabel
import se.birdy.app.ui.components.StampSeal
import se.birdy.app.ui.components.StampSealState
import se.birdy.app.ui.components.shimmerSweep
import se.birdy.app.ui.theme.MarginaliaInk
import se.birdy.app.ui.theme.rememberCaveat
import se.birdy.content.Locale

/** Hjälte-trofén: senast vunna stämpeln, stor, med nedtonad/långsam shimmer. Klick → detalj. */
@Composable
fun TrophyHero(
    hero: BadgeWithUnlock?,
    locale: Locale,
    zone: TimeZone,
    onHeroClick: (BadgeWithUnlock) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (hero != null) {
            val name = stringResource(BadgeStringMap.nameFor(hero.badge.id))
            Box(modifier = Modifier.shimmerSweep(durationMillis = 6000, alpha = 0.20f)) {
                StampSeal(
                    state = StampSealState.Unlocked(number = hero.stampNumber, glyph = null, name = name),
                    size = 104.dp,
                    onClick = { onHeroClick(hero) },
                )
            }
            Spacer(Modifier.height(8.dp))
            MicroLabel(stringResource(Res.string.trophy_hero_recent_label))
            Text(
                text = formatBadgeFullDate(hero.unlockedAt, zone, locale),
                color = MarginaliaInk,
                fontFamily = rememberCaveat(),
                fontSize = 15.sp,
            )
        } else {
            StampSeal(
                state = StampSealState.Locked(name = stringResource(Res.string.trophy_hero_empty_name)),
                size = 104.dp,
            )
        }
    }
}
