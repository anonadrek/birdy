package se.birdy.app.ui.recap

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.recap_day_a11y
import birdy_bird_scanner.composeapp.generated.resources.recap_day_future_a11y
import birdy_bird_scanner.composeapp.generated.resources.recap_day_none_a11y
import birdy_bird_scanner.composeapp.generated.resources.recap_ledger_a11y_fmt
import birdy_bird_scanner.composeapp.generated.resources.recap_ledger_finds
import birdy_bird_scanner.composeapp.generated.resources.recap_ledger_new_species
import birdy_bird_scanner.composeapp.generated.resources.recap_ledger_weeks
import birdy_bird_scanner.composeapp.generated.resources.recap_stats_finds
import birdy_bird_scanner.composeapp.generated.resources.recap_stats_new_species
import birdy_bird_scanner.composeapp.generated.resources.recap_stats_week_streak
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.recap.WeeklyRecapSummary
import se.birdy.app.ui.dailybird.dailyBirdDateA11yLabel
import se.birdy.app.ui.dailybird.weekdayShortRes
import se.birdy.app.ui.stats.wordFitFontSize
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.CardPaper
import se.birdy.app.ui.theme.InkMuted
import se.birdy.app.ui.theme.StampLocked
import se.birdy.app.ui.theme.TextOnHero
import se.birdy.app.ui.theme.rememberCaveat
import se.birdy.app.ui.theme.rememberDmSerifDisplay

private const val LEDGER_COLUMNS = 3
private const val DAYS_IN_WEEK = 7
private const val FUTURE_DAY_ALPHA = 0.45f
private const val DASHES_PER_CIRCLE = 16
private const val DASH_SHARE = 0.6f
private const val COUNT_BADGE_SP = 9f
private const val COUNT_BADGE_MAX_SCALE = 1.3f

/** Label sizes tried for the header row, largest first; all three labels share one size (as on Mina arter). */
private val LedgerLabelSizes: List<TextUnit> = listOf(9.5.sp, 9.sp, 8.5.sp)
private val LedgerNumberSize = 30.sp
private val StripPhotoMax = 38.dp
private val StripRing = 1.dp
private val StripGap = 1.5.dp

/**
 * The week's three numbers in the same header row as Mina arter (release 1.3.0 Task 7j): finds,
 * new species and weeks in a row, big rust DM Serif figures over small caps. TalkBack reads the
 * row as one sentence ("7 fynd, 3 nya arter, 4 veckor i rad."). When a caps word no longer fits a
 * third of the width (a large system font, English "SIGHTINGS"), the three stack as rows instead
 * of breaking a word.
 */
@Composable
internal fun RecapLedger(summary: WeeklyRecapSummary) {
    val counts = listOf(summary.observationCount, summary.newSpeciesCount, summary.weeklyStreak)
    val labels =
        listOf(
            pluralStringResource(Res.plurals.recap_ledger_finds, counts[0]),
            pluralStringResource(Res.plurals.recap_ledger_new_species, counts[1]),
            pluralStringResource(Res.plurals.recap_ledger_weeks, counts[2]),
        ).map { it.uppercase() }
    val sentence =
        stringResource(
            Res.string.recap_ledger_a11y_fmt,
            pluralStringResource(Res.plurals.recap_stats_finds, counts[0], counts[0]),
            pluralStringResource(Res.plurals.recap_stats_new_species, counts[1], counts[1]),
            pluralStringResource(Res.plurals.recap_stats_week_streak, counts[2], counts[2]),
        )
    val numberStyle = TextStyle(fontFamily = rememberDmSerifDisplay(), color = AccentCopper, lineHeight = 34.sp)
    // Merged, not cleared: the caps stay in the tree for the large-text guard, TalkBack reads the sentence.
    BoxWithConstraints(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = sentence }) {
        val cellWidth = maxWidth / LEDGER_COLUMNS - 8.dp
        val labelSize = wordFitFontSize(labels, ledgerLabelStyle(), LedgerLabelSizes, cellWidth)
        val numbers = counts.map { it.toString() }
        val numbersFit = wordFitFontSize(numbers, numberStyle, listOf(LedgerNumberSize), cellWidth) != null
        if (labelSize != null && numbersFit) {
            Row(Modifier.fillMaxWidth()) {
                counts.zip(labels).forEach { (count, label) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(count.toString(), style = numberStyle, fontSize = LedgerNumberSize)
                        Text(label, style = ledgerLabelStyle(), fontSize = labelSize, textAlign = TextAlign.Center)
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                counts.zip(labels).forEach { (count, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(count.toString(), style = numberStyle, fontSize = LedgerNumberSize)
                        Spacer(Modifier.width(12.dp))
                        Text(label, style = ledgerLabelStyle(), modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

private fun ledgerLabelStyle(): TextStyle =
    TextStyle(
        color = InkMuted,
        fontFamily = FontFamily.SansSerif,
        fontSize = LedgerLabelSizes.first(),
        fontWeight = FontWeight.W600,
        letterSpacing = 0.14.em,
        lineHeight = 1.25.em,
    )

/**
 * Monday to Sunday (release 1.3.0 Task 7j): each day's initial, a circle with the photo of that
 * day's latest find (ringed in rust) or a dashed empty circle, a count when there was more than
 * one find, and the date in Caveat. Days still to come get a fainter circle. TalkBack hears each day as
 * "måndag 5 oktober, 2 fynd".
 */
@Composable
internal fun WeekStrip(days: List<RecapDayItem>) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val photoSize = minOf(StripPhotoMax, maxWidth / DAYS_IN_WEEK - 6.dp)
        Row(Modifier.fillMaxWidth()) {
            days.forEach { day -> DayCell(day, photoSize, Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun DayCell(
    day: RecapDayItem,
    photoSize: Dp,
    modifier: Modifier = Modifier,
) {
    val label = dayA11yLabel(day)
    Column(
        modifier =
            modifier
                .semantics(mergeDescendants = true) { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(weekdayShortRes(day.date.dayOfWeek)).take(1).uppercase(),
            style = ledgerLabelStyle(),
            fontSize = 9.5.sp,
            letterSpacing = 0.12.em,
        )
        Spacer(Modifier.height(4.dp))
        Box(Modifier.size(photoSize)) {
            val cover = day.cover
            if (cover != null) {
                Box(
                    Modifier
                        .size(photoSize)
                        .border(StripRing, AccentCopper, CircleShape)
                        .padding(StripRing)
                        .clip(CircleShape)
                        .background(CardPaper)
                        .padding(StripGap),
                ) {
                    RecapPhoto(
                        find = cover,
                        shape = CircleShape,
                        modifier = Modifier.size(photoSize - (StripRing + StripGap) * 2),
                    )
                }
            } else {
                // A day still to come gets a fainter circle; its letter and date keep full contrast.
                Box(Modifier.size(photoSize).dashedCircle(faint = day.isFuture))
            }
            if (day.findCount > 1) {
                CountBadge(day.findCount, Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-2).dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = day.date.dayOfMonth.toString(),
            color = InkMuted,
            fontFamily = rememberCaveat(),
            fontSize = 15.sp,
            lineHeight = 1.1.em,
        )
    }
}

@Composable
private fun dayA11yLabel(day: RecapDayItem): String {
    val date = dailyBirdDateA11yLabel(day.date)
    return when {
        day.findCount > 0 -> pluralStringResource(Res.plurals.recap_day_a11y, day.findCount, date, day.findCount)
        day.isFuture -> stringResource(Res.string.recap_day_future_a11y, date)
        else -> stringResource(Res.string.recap_day_none_a11y, date)
    }
}

/**
 * The small rust count on a day with more than one find. It grows with the system font only up to
 * 1.3x: at 2.0x it covered half the day's photo, and TalkBack reads the count with the day anyway.
 */
@Composable
private fun CountBadge(
    count: Int,
    modifier: Modifier = Modifier,
) {
    val fontScale = LocalDensity.current.fontScale
    Text(
        text = count.toString(),
        color = TextOnHero,
        fontSize = (COUNT_BADGE_SP * minOf(fontScale, COUNT_BADGE_MAX_SCALE) / fontScale).sp,
        lineHeight = 1.2.em,
        fontWeight = FontWeight.W700,
        textAlign = TextAlign.Center,
        maxLines = 1,
        softWrap = false,
        modifier =
            modifier
                .widthIn(min = 16.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(AccentCopper)
                .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

/** A day without finds: an open dashed circle on the paper, like a locked stamp (decorative, never text). */
private fun Modifier.dashedCircle(faint: Boolean): Modifier =
    drawBehind {
        val stroke = 1.3.dp.toPx()
        val radius = (size.minDimension - stroke) / 2f
        val circumference = 2f * kotlin.math.PI.toFloat() * radius
        val segment = circumference / DASHES_PER_CIRCLE
        val dash = segment * DASH_SHARE
        drawCircle(
            color = if (faint) StampLocked.copy(alpha = FUTURE_DAY_ALPHA) else StampLocked,
            radius = radius,
            style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, segment - dash))),
        )
    }
