package se.birdy.app.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.stats_open_link
import birdy_bird_scanner.composeapp.generated.resources.stats_preview_species_this_year
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.stats.charts.JournalBarChart
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.BrassText
import se.birdy.app.ui.theme.CardPaper
import se.birdy.app.ui.theme.Hairline
import se.birdy.app.ui.theme.MarginaliaInk
import se.birdy.app.ui.theme.OutlineInk
import se.birdy.app.ui.theme.rememberCaveat
import se.birdy.app.ui.theme.rememberDmSerifDisplay

/**
 * Plan 6b3 T12: replaces [LockedStatsPreview] when premium is active. Shows a
 * mini bar-chart of the current calendar year + a species-count chip and a
 * tappable "Open stats →" link that navigates to [SeasonStatsScreen].
 *
 * 1.3.0 T12b review fix: surface moved from `SandCreme` to [CardPaper] + a 1dp [Hairline]
 * border, matching [se.birdy.app.ui.components.SectionCard]'s look — `SandCreme` made
 * [JournalBarChart]'s default `Hairline` axis ~1.17:1 (invisible) and its `Brass` current-month
 * bar ~2.47:1 (below 3:1); the card's own `axisColor` is now passed explicitly as [OutlineInk]
 * so a zero-observation month still has a visible baseline here.
 */
@Composable
fun LiveStatsPreview(
    state: SeasonStatsUiState.Loaded,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(shape)
                .background(CardPaper)
                .border(1.dp, Hairline, shape)
                .clickable(onClick = onOpen)
                .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = state.totalSpeciesThisYear.toString(),
                fontFamily = rememberDmSerifDisplay(),
                fontStyle = FontStyle.Italic,
                fontSize = 26.sp,
                color = AccentCopper,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                // T12c C1: was Res.string.stats_section_top, the stats screen's section title
                // ("Mest sedda" / "Most seen" since 1.3.0 Task 7c): "3 Most seen" next to
                // totalSpeciesThisYear reads false; this label exists only to caption the count,
                // not to name the section (the section's real title is drawn separately, above
                // this card, by LifelistScreen).
                // T12d Important 3: plural — "1 arter i år" read wrong for a first-species year.
                text =
                    pluralStringResource(
                        Res.plurals.stats_preview_species_this_year,
                        state.totalSpeciesThisYear,
                    ),
                fontFamily = rememberCaveat(),
                fontSize = 14.sp,
                color = MarginaliaInk,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(Res.string.stats_open_link),
                fontFamily = rememberCaveat(),
                fontSize = 14.sp,
                color = AccentCopper,
                fontWeight = FontWeight.W600,
            )
        }
        Spacer(Modifier.height(8.dp))
        JournalBarChart(
            bars = state.monthBars,
            modifier = Modifier.fillMaxWidth(),
            axisColor = OutlineInk,
            height = 70.dp,
        )
        Spacer(Modifier.height(2.dp))
        CurrentMonthCaption(state.monthBars)
    }
}

/**
 * T12d Important 2: was a single Text, start-aligned under the whole row — reading e.g. "SEP"
 * flush left while the current-month bar sits under the ninth of twelve slots. Rebuilds the
 * chart's own slot geometry (12 equal-weight cells) so the one caption we render lands centred
 * under its own bar, matching JournalBarChart/MonthLabelsRow's layout. Not a 12-label row like
 * the full stats screen: [LiveStatsPreview]'s card is `clickable` (mergeDescendants), so 12
 * month names would all be read aloud together. Split out of [LiveStatsPreview] to keep that
 * composable under detekt's LongMethod ceiling.
 */
@Composable
private fun CurrentMonthCaption(bars: List<SeasonStatsUiState.MonthBar>) {
    Row(Modifier.fillMaxWidth()) {
        bars.forEach { b ->
            Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                if (b.isCurrent) {
                    Text(
                        text = b.label,
                        fontFamily = rememberCaveat(),
                        fontSize = 12.sp,
                        color = BrassText,
                        fontWeight = FontWeight.W700,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.wrapContentWidth(unbounded = true),
                    )
                }
            }
        }
    }
}
