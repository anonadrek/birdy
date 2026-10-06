package se.birdy.app.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.stats_seal_count
import birdy_bird_scanner.composeapp.generated.resources.stats_section_top
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.components.MicroLabel
import se.birdy.app.ui.components.SectionCard
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.CardPaper
import se.birdy.app.ui.theme.TextOnCreme
import se.birdy.app.ui.theme.rememberCaveat
import se.birdy.app.ui.theme.rememberDmSerifDisplay

private const val SEAL_COLUMNS = 3
private const val SEAL_HAIRLINE_ALPHA = 0.35f
private val SealSize = 64.dp
private val SealRowSize = 52.dp
private val SealRing = 3.5.dp
private val SealGap = 2.dp

/** Name sizes tried for the three seal columns, largest first; all three names share one size. */
private val SealNameSizes: List<TextUnit> = listOf(15.sp, 14.sp, 13.sp, 12.sp)

/**
 * "Mest sedda" (release 1.3.0 Task 7c, design option B): the year's three most seen species as
 * embossed seals, StampSeal's rust ring and soft shadow around the species' plate photo, with the
 * name and the number of finds under each. One or two species sit centered, not left in a grid.
 *
 * The name size is decided once for all three, like the old totals row: the largest size at which
 * every word of every name fits its column. If even the smallest does not fit (a long compound name
 * at a large system font on a narrow phone), the seals stack as rows instead, so no name is ever
 * broken mid-word.
 */
@Composable
internal fun TopSpeciesSeals(
    rows: List<SeasonStatsUiState.TopSpeciesRow>,
    modifier: Modifier = Modifier,
) {
    SectionCard(modifier = modifier.padding(horizontal = 24.dp)) {
        MicroLabel(
            text = stringResource(Res.string.stats_section_top),
            modifier = Modifier.semantics(mergeDescendants = true) { heading() },
        )
        Spacer(Modifier.height(12.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columnWidth = maxWidth / SEAL_COLUMNS
            val nameSize = sealNameSize(rows.map { it.nameLocalized }, columnWidth - 8.dp)
            if (nameSize != null) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    rows.forEach { row ->
                        key(row.qid) { SealColumn(row, nameSize, Modifier.width(columnWidth)) }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    rows.forEach { row -> key(row.qid) { SealRow(row) } }
                }
            }
        }
    }
}

/** The largest [SealNameSizes] entry at which every word of every name fits [maxWidth], or null. */
@Composable
private fun sealNameSize(
    names: List<String>,
    maxWidth: Dp,
): TextUnit? = wordFitFontSize(names, TextStyle(fontFamily = rememberDmSerifDisplay()), SealNameSizes, maxWidth)

@Composable
private fun SealColumn(
    row: SeasonStatsUiState.TopSpeciesRow,
    nameSize: TextUnit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PhotoSeal(row, SealSize)
        Spacer(Modifier.height(8.dp))
        Text(
            text = row.nameLocalized,
            fontFamily = rememberDmSerifDisplay(),
            fontSize = nameSize,
            lineHeight = 1.15.em,
            color = TextOnCreme,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        SealCount(row.count, TextAlign.Center)
    }
}

@Composable
private fun SealRow(row: SeasonStatsUiState.TopSpeciesRow) {
    Row(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PhotoSeal(row, SealRowSize)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            FittedSpeciesName(
                text = row.nameLocalized,
                style = TextStyle(fontFamily = rememberDmSerifDisplay(), color = TextOnCreme, lineHeight = 1.15.em),
                fontSize = 15.sp,
                minFontSize = 10.sp,
            )
            SealCount(row.count, TextAlign.Start)
        }
    }
}

@Composable
private fun SealCount(
    count: Int,
    textAlign: TextAlign,
) {
    Text(
        text = pluralStringResource(Res.plurals.stats_seal_count, count, count),
        fontFamily = rememberCaveat(),
        fontSize = 16.sp,
        lineHeight = 1.1.em,
        color = AccentCopper,
        textAlign = textAlign,
    )
}

/**
 * The seal itself: StampSeal's embossed look (rust ring, light inner hairline, soft drop shadow)
 * around the species' plate photo, with a paper gap between ring and photo.
 */
@Composable
private fun PhotoSeal(
    row: SeasonStatsUiState.TopSpeciesRow,
    size: Dp,
) {
    Box(
        modifier =
            Modifier
                .size(size)
                .shadow(3.dp, CircleShape)
                .clip(CircleShape)
                .background(AccentCopper)
                .drawWithContent {
                    drawContent()
                    drawCircle(
                        color = Color.White.copy(alpha = SEAL_HAIRLINE_ALPHA),
                        radius = this.size.minDimension / 2 - SealRing.toPx() / 2,
                        style = Stroke(width = 0.75.dp.toPx()),
                    )
                }.padding(SealRing)
                .clip(CircleShape)
                .background(CardPaper)
                .padding(SealGap),
    ) {
        StatsSpeciesPhoto(
            name = row.nameLocalized,
            heroImagePath = row.heroImagePath,
            size = size - (SealRing + SealGap) * 2,
        )
    }
}
