package se.birdy.app.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.stats_first_seen_date
import birdy_bird_scanner.composeapp.generated.resources.stats_first_sighting_description
import birdy_bird_scanner.composeapp.generated.resources.stats_first_sighting_number
import birdy_bird_scanner.composeapp.generated.resources.stats_section_firsts
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.components.MicroLabel
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.CardPaper
import se.birdy.app.ui.theme.Hairline
import se.birdy.app.ui.theme.InkMuted
import se.birdy.app.ui.theme.TextOnCreme
import se.birdy.app.ui.theme.rememberCaveat
import se.birdy.app.ui.theme.rememberDmSerifDisplay

private val CardMargin = 24.dp
private val CardPadding = 14.dp
private val CardCorner = 16.dp
private val PhotoSize = 42.dp
private val RowPadding = 5.dp
private val PhotoRing = 1.dp
private val PhotoGap = 1.5.dp

/**
 * "Nya arter i år" (release 1.3.0 Task 7c, design option B): this year's first find of each
 * species as a timeline, earliest first, with date, name, plate photo and its number in the year
 * list. Replaces the old cumulative line chart: the same data, told as moments.
 *
 * Every row is its own lazy item (a busy year can hold hundreds of species), drawn as a slice of
 * one card: [cardSlice] paints the card's fill and hairline only on the edges this slice owns, so
 * the header and the rows together read as a single [se.birdy.app.ui.components.SectionCard].
 */
internal fun LazyListScope.firstSightingsTimeline(rows: List<SeasonStatsUiState.FirstSightingRow>) {
    if (rows.isEmpty()) return
    item(key = "firsts-header") { TimelineHeader() }
    itemsIndexed(rows, key = { _, row -> "first-${row.qid}" }) { index, row ->
        TimelineRow(row = row, isFirst = index == 0, isLast = index == rows.lastIndex)
    }
}

@Composable
private fun TimelineHeader() {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = CardMargin)
                .cardSlice(top = true, bottom = false)
                .padding(start = CardPadding, end = CardPadding, top = CardPadding, bottom = 6.dp),
    ) {
        MicroLabel(
            text = stringResource(Res.string.stats_section_firsts),
            // Same as the other section kickers: merge so the heading node carries the text.
            modifier = Modifier.semantics(mergeDescendants = true) { heading() },
        )
    }
}

@Composable
private fun TimelineRow(
    row: SeasonStatsUiState.FirstSightingRow,
    isFirst: Boolean,
    isLast: Boolean,
) {
    val date = stringResource(Res.string.stats_first_seen_date, row.dayOfMonth, fullMonthNames()[row.month - 1])
    val description = stringResource(Res.string.stats_first_sighting_description, row.nameLocalized, row.number, date)
    val bottomPadding = if (isLast) CardPadding else RowPadding
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = CardMargin)
                .cardSlice(top = false, bottom = isLast)
                .padding(start = CardPadding, end = CardPadding, top = RowPadding, bottom = bottomPadding)
                .timelineLine(above = !isFirst, below = !isLast, topPadding = RowPadding, bottomPadding = bottomPadding)
                .semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RingedPhoto(name = row.nameLocalized, heroImagePath = row.heroImagePath, size = PhotoSize)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = date,
                fontFamily = rememberCaveat(),
                fontSize = 15.sp,
                lineHeight = 1.1.em,
                color = InkMuted,
            )
            FittedSpeciesName(
                text = row.nameLocalized,
                style = TextStyle(fontFamily = rememberDmSerifDisplay(), color = TextOnCreme, lineHeight = 1.15.em),
                fontSize = 17.sp,
                minFontSize = 11.sp,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(Res.string.stats_first_sighting_number, row.number),
            fontFamily = rememberCaveat(),
            fontSize = 16.sp,
            color = AccentCopper,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** The timeline's photo: a thin hairline ring with a paper gap, like the mockup's avatar. */
@Composable
private fun RingedPhoto(
    name: String,
    heroImagePath: String?,
    size: Dp,
) {
    Box(
        modifier =
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(Hairline)
                .padding(PhotoRing)
                .clip(CircleShape)
                .background(CardPaper)
                .padding(PhotoGap),
    ) {
        StatsSpeciesPhoto(name = name, heroImagePath = heroImagePath, size = size - (PhotoRing + PhotoGap) * 2)
    }
}

/**
 * The timeline's thread: a hairline through the photo centers, from the row above to the row
 * below. Drawn behind the row's content, so each photo sits on top of it; it reaches into the
 * row's own padding ([topPadding], [bottomPadding]) to meet the neighbouring rows' segments.
 */
private fun Modifier.timelineLine(
    above: Boolean,
    below: Boolean,
    topPadding: Dp,
    bottomPadding: Dp,
): Modifier =
    if (!above && !below) {
        this
    } else {
        drawBehind {
            val x = PhotoSize.toPx() / 2
            val middle = size.height / 2
            val stroke = 1.dp.toPx()
            if (above) drawLine(Hairline, Offset(x, -topPadding.toPx()), Offset(x, middle), stroke)
            if (below) drawLine(Hairline, Offset(x, middle), Offset(x, size.height + bottomPadding.toPx()), stroke)
        }
    }

/**
 * One horizontal slice of a [se.birdy.app.ui.components.SectionCard]-looking card: CardPaper fill
 * and a 1dp [Hairline] edge with 16dp corners, but only on the sides this slice owns. The rounded
 * rectangle is drawn taller than the slice on its open sides and clipped to the slice, so the
 * slices stack into one seamless card.
 */
private fun Modifier.cardSlice(
    top: Boolean,
    bottom: Boolean,
): Modifier =
    drawBehind {
        val radius = CardCorner.toPx()
        val stroke = 1.dp.toPx()
        val overhang = radius + stroke
        val rectTop = if (top) 0f else -overhang
        val rectBottom = if (bottom) size.height else size.height + overhang
        clipRect(0f, 0f, size.width, size.height) {
            drawRoundRect(
                color = CardPaper,
                topLeft = Offset(0f, rectTop),
                size = Size(size.width, rectBottom - rectTop),
                cornerRadius = CornerRadius(radius),
            )
            drawRoundRect(
                color = Hairline,
                topLeft = Offset(stroke / 2, rectTop + stroke / 2),
                size = Size(size.width - stroke, rectBottom - rectTop - stroke),
                cornerRadius = CornerRadius(radius - stroke / 2),
                style = Stroke(width = stroke),
            )
        }
    }
