package se.birdy.app.ui.dailybird

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.daily_bird_badge_complete
import birdy_bird_scanner.composeapp.generated.resources.daily_bird_card_eyebrow
import birdy_bird_scanner.composeapp.generated.resources.daily_bird_caught_today
import birdy_bird_scanner.composeapp.generated.resources.daily_bird_challenge_a11y
import birdy_bird_scanner.composeapp.generated.resources.daily_bird_days_left
import birdy_bird_scanner.composeapp.generated.resources.daily_bird_days_of_target
import birdy_bird_scanner.composeapp.generated.resources.daily_bird_days_total
import birdy_bird_scanner.composeapp.generated.resources.daily_bird_listen_for_it
import birdy_bird_scanner.composeapp.generated.resources.daily_bird_not_caught
import birdy_bird_scanner.composeapp.generated.resources.daily_bird_one_day_left
import birdy_bird_scanner.composeapp.generated.resources.daily_bird_read_more
import birdy_bird_scanner.composeapp.generated.resources.daily_bird_save_a_find_today
import birdy_bird_scanner.composeapp.generated.resources.daily_bird_strip_a11y
import birdy_bird_scanner.composeapp.generated.resources.daily_bird_strip_status_fmt
import birdy_bird_scanner.composeapp.generated.resources.daily_bird_two_days_left
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.dailybird.DailyBirdChallenge
import se.birdy.app.dailybird.DailyBirdChallengeLine
import se.birdy.app.dailybird.DailyBirdCountStyle
import se.birdy.app.dailybird.DailyBirdToday
import se.birdy.app.dailybird.challenge
import se.birdy.app.ui.components.LATIN_NAME_TEXT_ALPHA
import se.birdy.app.ui.components.MicroLabel
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.AccentCopperLight
import se.birdy.app.ui.theme.CardPaper
import se.birdy.app.ui.theme.Hairline
import se.birdy.app.ui.theme.HeroMossDeep
import se.birdy.app.ui.theme.HeroMossLight
import se.birdy.app.ui.theme.HeroMossMid
import se.birdy.app.ui.theme.InkMuted
import se.birdy.app.ui.theme.MossCreme
import se.birdy.app.ui.theme.PhotoLoading
import se.birdy.app.ui.theme.TextOnCreme
import se.birdy.app.ui.theme.TextOnHero
import se.birdy.app.ui.theme.rememberCaveat
import se.birdy.app.ui.theme.rememberDmSerifDisplay
import se.birdy.app.util.speciesImageUri

// Text on the hero's text area (PhotoHero's own text-following scrim). PhotoHeroContrastTest
// proves TextOnHero at META_TEXT_ALPHA (0.75) and up clears WCAG AA there; every alpha below is
// at least LATIN_NAME_TEXT_ALPHA (0.85), so these lines need no scrim of their own.
private const val HERO_LABEL_ALPHA = 0.9f
private const val HERO_COUNT_ALPHA = 0.88f
private const val HERO_RULE_ALPHA = 0.28f

// The outlined hero button's border: a graphical boundary, 3:1 is enough. ≈3.72:1 over PhotoHero's
// neutral text scrim (black 0.70 over a pure-white photo), pinned by DailyBirdHeroOutlineContrastTest.
internal const val HERO_OUTLINE_ALPHA = 0.55f

private val PillShape = RoundedCornerShape(percent = 50)
private val StripShape = RoundedCornerShape(16.dp)

/** Status, handwritten line and day count of a [DailyBirdChallenge], in the current language. */
internal data class DailyBirdChallengeTexts(
    val status: String,
    val line: String,
    val count: String,
)

@Composable
internal fun challengeTexts(challenge: DailyBirdChallenge): DailyBirdChallengeTexts =
    DailyBirdChallengeTexts(
        status =
            stringResource(
                if (challenge.caughtToday) Res.string.daily_bird_caught_today else Res.string.daily_bird_not_caught,
            ),
        line =
            when (challenge.line) {
                DailyBirdChallengeLine.SAVE_A_FIND_TODAY -> stringResource(Res.string.daily_bird_save_a_find_today)
                DailyBirdChallengeLine.DAYS_LEFT_TO_BADGE -> daysLeftLine(challenge.daysLeft)
                DailyBirdChallengeLine.BADGE_COMPLETE -> stringResource(Res.string.daily_bird_badge_complete)
            },
        count =
            when (challenge.countStyle) {
                DailyBirdCountStyle.OF_TARGET ->
                    pluralStringResource(
                        Res.plurals.daily_bird_days_of_target,
                        challenge.seals,
                        challenge.daysCaught,
                        challenge.seals,
                    )
                DailyBirdCountStyle.TOTAL ->
                    pluralStringResource(Res.plurals.daily_bird_days_total, challenge.daysCaught, challenge.daysCaught)
            },
    )

/**
 * "En dag" / "Två dagar kvar till märket.": with the target 3 and today's catch counted, only 1 or 2
 * days can be left, written out. The plural is only a fallback should the badge target ever grow.
 */
@Composable
private fun daysLeftLine(daysLeft: Int): String =
    when (daysLeft) {
        1 -> stringResource(Res.string.daily_bird_one_day_left)
        2 -> stringResource(Res.string.daily_bird_two_days_left)
        else -> pluralStringResource(Res.plurals.daily_bird_days_left, daysLeft, daysLeft)
    }

/**
 * The two actions under the hero's names (design option B): "Läs om arten" (paper pill, opens the
 * profile) and "Lyssna efter den" (outlined, opens audio ID). A FlowRow so the second button
 * wraps under the first at large font scales instead of being clipped.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DailyBirdHeroActions(
    onReadMore: () -> Unit,
    onListen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HeroPill(
            text = stringResource(Res.string.daily_bird_read_more),
            onClick = onReadMore,
            filled = true,
            trailingIcon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        )
        HeroPill(
            text = stringResource(Res.string.daily_bird_listen_for_it),
            onClick = onListen,
            filled = false,
            leadingIcon = Icons.Filled.Hearing,
        )
    }
}

@Suppress("LongParameterList") // text/onClick/variant + optional leading/trailing icon is the whole API.
@Composable
private fun HeroPill(
    text: String,
    onClick: () -> Unit,
    filled: Boolean,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
) {
    val contentColor = if (filled) TextOnCreme else TextOnHero
    Row(
        modifier =
            modifier
                // 48dp touch target around a ~34dp pill (mockup: 30px button).
                .minimumInteractiveComponentSize()
                .clip(PillShape)
                .let { m ->
                    if (filled) {
                        m.background(MossCreme)
                    } else {
                        m.border(1.dp, TextOnHero.copy(alpha = HERO_OUTLINE_ALPHA), PillShape)
                    }
                }.clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, tint = contentColor, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text = text, color = contentColor, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.W600)
        if (trailingIcon != null) {
            Spacer(Modifier.width(2.dp))
            Icon(trailingIcon, contentDescription = null, tint = contentColor, modifier = Modifier.size(16.dp))
        }
    }
}

/**
 * The challenge row on the hero (light on its dark text area): status with a check once caught,
 * a handwritten line on what to do, three seals and the day count. Read out as one sentence.
 *
 * Dagens fågel-jägare is a Premium badge (premium_badges.yaml), but the row is for everyone; for a
 * user without Premium ([DailyBirdChallenge.showPremiumBadgeTag]) a "Premium-märke" tag sits under
 * the day count and is read out after it (Albin, 2026-10-07).
 */
@Composable
internal fun DailyBirdHeroChallengeRow(
    challenge: DailyBirdChallenge,
    modifier: Modifier = Modifier,
) {
    val texts = challengeTexts(challenge)
    val a11y =
        withPremiumBadgeTag(
            stringResource(Res.string.daily_bird_challenge_a11y, texts.status, texts.line, texts.count),
            challenge.showPremiumBadgeTag,
        )
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .drawBehind {
                    drawLine(
                        color = TextOnHero.copy(alpha = HERO_RULE_ALPHA),
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        strokeWidth = 1.dp.toPx(),
                    )
                }.padding(top = 10.dp)
                .clearAndSetSemantics { contentDescription = a11y },
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
            if (challenge.showPremiumBadgeTag) {
                Spacer(Modifier.height(5.dp))
                DailyBirdPremiumBadgeTag()
            }
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

/** Ring/fill and tick colours of a [DailyBirdSeals] row. */
internal data class SealColors(
    val ring: Color,
    val check: Color,
)

// On the dark hero: apricot seals with a moss tick (mockup "m-dots"). On paper: rust with a light tick.
private val HeroSealColors = SealColors(ring = AccentCopperLight, check = HeroMossDeep)
internal val PaperSealColors = SealColors(ring = AccentCopper, check = TextOnHero)

// The tick inside a filled seal, as fractions of the seal's size.
private const val TICK_START_X = 0.30f
private const val TICK_START_Y = 0.52f
private const val TICK_MID_X = 0.44f
private const val TICK_MID_Y = 0.66f
private const val TICK_END_X = 0.71f
private const val TICK_END_Y = 0.38f

/** [total] round seals, the first [filled] filled with a check (the mockup's "m-dots"). Decorative. */
@Composable
internal fun DailyBirdSeals(
    filled: Int,
    total: Int,
    sealSize: Dp,
    colors: SealColors,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(total) { index -> Seal(on = index < filled, size = sealSize, colors = colors) }
    }
}

@Composable
private fun Seal(
    on: Boolean,
    size: Dp,
    colors: SealColors,
) {
    Canvas(Modifier.size(size)) {
        val stroke = 1.5.dp.toPx()
        val radius = (this.size.minDimension - stroke) / 2f
        if (on) {
            drawCircle(color = colors.ring, radius = this.size.minDimension / 2f)
            val w = this.size.width
            val h = this.size.height
            val tick =
                Path().apply {
                    moveTo(w * TICK_START_X, h * TICK_START_Y)
                    lineTo(w * TICK_MID_X, h * TICK_MID_Y)
                    lineTo(w * TICK_END_X, h * TICK_END_Y)
                }
            drawPath(
                tick,
                color = colors.check,
                style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        } else {
            drawCircle(color = colors.ring, radius = radius, style = Stroke(width = stroke))
        }
    }
}

/**
 * The slim Dagens fågel strip on Mina arter and Uppslagsverk (design option B): round photo,
 * kicker, name, status with the day count, chevron. Shown all day; opening it opens the profile.
 * Without Premium the day count gets the same "Premium-märke" tag as the hero's challenge row,
 * beside the status or under it when the line is full (large text).
 */
@Composable
fun DailyBirdStrip(
    bird: DailyBirdToday,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val serif = rememberDmSerifDisplay()
    val caveat = rememberCaveat()
    val challenge = bird.challenge()
    val texts = challengeTexts(challenge)
    val status = stringResource(Res.string.daily_bird_strip_status_fmt, texts.status, texts.count)
    val a11y =
        withPremiumBadgeTag(
            stringResource(Res.string.daily_bird_strip_a11y, bird.name, texts.status, texts.count),
            challenge.showPremiumBadgeTag,
        )
    val readMore = stringResource(Res.string.daily_bird_read_more)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .shadow(elevation = 3.dp, shape = StripShape, ambientColor = HeroMossDeep, spotColor = HeroMossDeep)
                .clip(StripShape)
                .background(CardPaper)
                .border(1.dp, Hairline, StripShape)
                .clickable(role = Role.Button, onClickLabel = readMore, onClick = onClick)
                .semantics(mergeDescendants = true) { contentDescription = a11y }
                .padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StripAvatar(heroImagePath = bird.heroImagePath)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            MicroLabel(stringResource(Res.string.daily_bird_card_eyebrow), fontSize = 8.5.sp)
            Text(
                text = bird.name,
                color = TextOnCreme,
                fontFamily = serif,
                fontSize = 19.sp,
                lineHeight = 22.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp),
            )
            DailyBirdStripStatus(challenge = challenge, status = status, caveat = caveat)
        }
        Spacer(Modifier.width(6.dp))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = InkMuted)
    }
}

@Composable
private fun StripAvatar(heroImagePath: String?) {
    // Apricot ring with a thin paper gap around the photo (mockup: box-shadow 2px card + 3.5px apricot).
    Box(
        modifier =
            Modifier
                .size(48.dp)
                .border(1.5.dp, AccentCopperLight, CircleShape)
                .padding(3.5.dp)
                .clip(CircleShape)
                // Behind the photo: neutral while it loads, never a green flash (2026-10-06).
                // No photo: the moss placeholder, as designed.
                .then(
                    if (heroImagePath != null) {
                        Modifier.background(PhotoLoading)
                    } else {
                        Modifier.background(Brush.verticalGradient(listOf(HeroMossLight, HeroMossMid, HeroMossDeep)))
                    },
                ),
    ) {
        if (heroImagePath != null) {
            AsyncImage(
                model = speciesImageUri(heroImagePath),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
