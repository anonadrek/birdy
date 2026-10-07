package se.birdy.app.ui.badges

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.trophy_room_entry_empty_a11y
import birdy_bird_scanner.composeapp.generated.resources.trophy_room_entry_eyebrow
import birdy_bird_scanner.composeapp.generated.resources.trophy_room_entry_first_stamp
import birdy_bird_scanner.composeapp.generated.resources.trophy_room_entry_latest
import birdy_bird_scanner.composeapp.generated.resources.trophy_room_entry_stamps
import birdy_bird_scanner.composeapp.generated.resources.trophy_room_entry_stamps_a11y
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.components.HeadlineSegment
import se.birdy.app.ui.components.MicroLabel
import se.birdy.app.ui.components.parseJournalHeadline
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.CardPaper
import se.birdy.app.ui.theme.Hairline
import se.birdy.app.ui.theme.MarginaliaInk
import se.birdy.app.ui.theme.TextOnCreme
import se.birdy.app.ui.theme.rememberCaveat
import se.birdy.app.ui.theme.rememberDmSerifDisplay
import se.birdy.content.Locale

private val EntryShape = RoundedCornerShape(16.dp)
private val TextToFanGap = 12.dp

/** What the card says. [count] is null on the empty card, which has only the [line]. */
@Immutable
private data class EntryTexts(
    val count: String?,
    val line: String,
    val a11y: String,
)

/**
 * The card on Märken that opens Troférummet (release 1.3.0 Task 7k, design "Bild 2"): paper with
 * "Ditt troférum", the stamp count and the latest stamp on the left, and your three most recently
 * earned seals fanned out on the right, so the card changes as you collect. With no stamps yet it
 * shows one dashed seal and "Din första stämpel väntar." Drawn by the app, so no photo licence:
 * it replaced a stock photo of unknown origin (files/branding, removed).
 *
 * [recent] is any list of earned stamps (the Märken screen passes its recently unlocked list);
 * the card picks the three newest itself. [unlockedCount] is the Märken tab's stamp count.
 * TalkBack reads the whole card as one button. When the text would not fit beside the seals (large
 * text on a narrow phone), the seals move below it instead of a word breaking in two.
 */
@Suppress("LongParameterList") // locale, zone and now feed the badge date formatter, as on BadgeRecentCard.
@Composable
fun TrophyRoomEntryCard(
    recent: List<BadgeWithUnlock>,
    unlockedCount: Int,
    locale: Locale,
    zone: TimeZone,
    now: Instant,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val stamps = trophyFanStamps(recent)
    val texts =
        entryTexts(latest = stamps.lastOrNull(), unlockedCount = unlockedCount, locale = locale, zone = zone, now = now)
    val countStyle = countTextStyle()
    val lineStyle = lineTextStyle(empty = texts.count == null)
    val textNeeds = textNeeds(texts, countStyle, lineStyle)

    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .clip(EntryShape)
                .background(CardPaper)
                .border(1.dp, Hairline, EntryShape)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) { contentDescription = texts.a11y }
                .padding(start = 16.dp, end = 14.dp, top = 14.dp, bottom = 14.dp),
    ) {
        val fan: @Composable (Modifier) -> Unit = { m ->
            if (stamps.isNotEmpty()) TrophyStampFan(stamps, m) else TrophyStampWaiting(m)
        }
        val sideBySide = textNeeds <= maxWidth - trophyFanWidth(stamps.size) - TextToFanGap
        if (sideBySide) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TextToFanGap),
            ) {
                EntryTextBlock(texts, countStyle, lineStyle, Modifier.weight(1f))
                fan(Modifier)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EntryTextBlock(texts, countStyle, lineStyle, Modifier.fillMaxWidth())
                fan(Modifier.align(Alignment.End))
            }
        }
    }
}

@Composable
private fun entryTexts(
    latest: BadgeWithUnlock?,
    unlockedCount: Int,
    locale: Locale,
    zone: TimeZone,
    now: Instant,
): EntryTexts {
    if (latest == null) {
        return EntryTexts(
            count = null,
            line = stringResource(Res.string.trophy_room_entry_first_stamp),
            a11y = stringResource(Res.string.trophy_room_entry_empty_a11y),
        )
    }
    val name = stringResource(BadgeStringMap.nameFor(latest.badge.id))
    val date = formatRelativeBadgeDate(latest.unlockedAt, now, zone, locale)
    return EntryTexts(
        count = pluralStringResource(Res.plurals.trophy_room_entry_stamps, unlockedCount, unlockedCount),
        line = stringResource(Res.string.trophy_room_entry_latest, name, date),
        a11y =
            pluralStringResource(Res.plurals.trophy_room_entry_stamps_a11y, unlockedCount, unlockedCount, name, date),
    )
}

@Composable
private fun EntryTextBlock(
    texts: EntryTexts,
    countStyle: TextStyle,
    lineStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        MicroLabel(stringResource(Res.string.trophy_room_entry_eyebrow))
        if (texts.count != null) Text(text = stampCountText(texts.count), style = countStyle)
        Text(text = texts.line, style = lineStyle)
    }
}

@Composable
private fun countTextStyle(): TextStyle {
    val serif = rememberDmSerifDisplay()
    return LocalTextStyle.current.merge(
        TextStyle(
            color = TextOnCreme,
            fontFamily = serif,
            fontSize = 22.sp,
            lineHeight = 26.sp,
            letterSpacing = (-0.01).em,
        ),
    )
}

@Composable
private fun lineTextStyle(empty: Boolean): TextStyle {
    val caveat = rememberCaveat()
    return LocalTextStyle.current.merge(
        if (empty) {
            TextStyle(color = MarginaliaInk, fontFamily = caveat, fontSize = 16.sp, lineHeight = 19.sp)
        } else {
            TextStyle(color = MarginaliaInk, fontFamily = caveat, fontSize = 15.sp, lineHeight = 18.sp)
        },
    )
}

/**
 * How wide the text beside the seals must be, at the current text size: the count ("8 stämplar")
 * on one line and the widest single word of the handwritten line. Narrower than that, the count
 * would split or a word would break in two, so the seals go below the text instead.
 */
@Composable
private fun textNeeds(
    texts: EntryTexts,
    countStyle: TextStyle,
    lineStyle: TextStyle,
): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(texts, countStyle, lineStyle, measurer, density) {
        val countWidth = texts.count?.let { measurer.measure(stampCountText(it), countStyle).size.width } ?: 0
        val lineWords = texts.line.split(' ').map { measurer.measure(it, lineStyle).size.width }
        with(density) { maxOf(countWidth, lineWords.maxOrNull() ?: 0).toDp() }
    }
}

/** "8 *stämplar*": the number upright, the word in rust italic, as in every journal headline. */
private fun stampCountText(text: String): AnnotatedString =
    buildAnnotatedString {
        parseJournalHeadline(text).forEach { segment ->
            when (segment) {
                is HeadlineSegment.Plain -> append(segment.text)
                is HeadlineSegment.Accent ->
                    withStyle(SpanStyle(color = AccentCopper, fontStyle = FontStyle.Italic)) { append(segment.text) }
            }
        }
    }
