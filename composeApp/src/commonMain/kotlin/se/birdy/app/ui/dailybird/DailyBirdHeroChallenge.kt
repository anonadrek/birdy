package se.birdy.app.ui.dailybird

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import se.birdy.app.dailybird.DailyBirdChallenge
import se.birdy.app.ui.components.LATIN_NAME_TEXT_ALPHA
import se.birdy.app.ui.theme.AccentCopperLight
import se.birdy.app.ui.theme.HeroMossDeep
import se.birdy.app.ui.theme.TextOnHero
import se.birdy.app.ui.theme.rememberCaveat

// Text on the hero's text area (PhotoHero's own text-following scrim). PhotoHeroContrastTest
// proves TextOnHero at META_TEXT_ALPHA (0.75) and up clears WCAG AA there; every alpha below is
// at least LATIN_NAME_TEXT_ALPHA (0.85), so these lines need no scrim of their own.
private const val HERO_LABEL_ALPHA = 0.9f
private const val HERO_COUNT_ALPHA = 0.88f

// On the dark hero: apricot seals with a moss tick (mockup "m-dots").
private val HeroSealColors = SealColors(ring = AccentCopperLight, check = HeroMossDeep)

/**
 * What [DailyBirdHeroChallengeRow] lays out: status and handwritten line, seals and day count, and
 * for a user without Premium the "Premium-märke" tag. Separate from the row, which reads it all
 * out as one sentence (clearAndSetSemantics), so a test can check every line's own layout.
 *
 * The tag has a line of its own under the row, at its end: beside the day count it took the
 * status's width, and at 200 % text on a 320dp phone "INTE FÅNGAD IDAG" broke mid-word.
 */
@Composable
internal fun DailyBirdHeroChallengeContent(
    challenge: DailyBirdChallenge,
    texts: DailyBirdChallengeTexts,
) {
    Column(Modifier.fillMaxWidth()) {
        HeroChallengeStatusAndCount(challenge = challenge, texts = texts)
        if (challenge.showPremiumBadgeTag) {
            Spacer(Modifier.height(6.dp))
            DailyBirdPremiumBadgeTag(Modifier.align(Alignment.End))
        }
    }
}

@Composable
private fun HeroChallengeStatusAndCount(
    challenge: DailyBirdChallenge,
    texts: DailyBirdChallengeTexts,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        HeroChallengeStatus(caughtToday = challenge.caughtToday, texts = texts, modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            DailyBirdSeals(
                filled = challenge.filledSeals,
                total = challenge.seals,
                sealSize = 15.dp,
                colors = HeroSealColors,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = texts.count,
                color = TextOnHero.copy(alpha = HERO_COUNT_ALPHA),
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.W600,
                fontSize = 9.5.sp,
                lineHeight = 12.sp,
                letterSpacing = 0.08.em,
            )
        }
    }
}

@Composable
private fun HeroChallengeStatus(
    caughtToday: Boolean,
    texts: DailyBirdChallengeTexts,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (caughtToday) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = AccentCopperLight,
                    modifier = Modifier.size(13.dp),
                )
                Spacer(Modifier.width(5.dp))
            }
            Text(
                text = texts.status.uppercase(),
                color = TextOnHero.copy(alpha = HERO_LABEL_ALPHA),
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.W600,
                fontSize = 9.5.sp,
                lineHeight = 12.sp,
                letterSpacing = 0.15.em,
            )
        }
        Text(
            text = texts.line,
            color = TextOnHero.copy(alpha = LATIN_NAME_TEXT_ALPHA),
            fontFamily = rememberCaveat(),
            fontSize = 17.sp,
            lineHeight = 19.sp,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}
