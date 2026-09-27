package se.birdy.app.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.premium_badge
import birdy_bird_scanner.composeapp.generated.resources.stats_autumn
import birdy_bird_scanner.composeapp.generated.resources.stats_back
import birdy_bird_scanner.composeapp.generated.resources.stats_count_format
import birdy_bird_scanner.composeapp.generated.resources.stats_empty_eyebrow
import birdy_bird_scanner.composeapp.generated.resources.stats_empty_headline
import birdy_bird_scanner.composeapp.generated.resources.stats_empty_sub
import birdy_bird_scanner.composeapp.generated.resources.stats_intro_eyebrow
import birdy_bird_scanner.composeapp.generated.resources.stats_intro_headline
import birdy_bird_scanner.composeapp.generated.resources.stats_intro_sub
import birdy_bird_scanner.composeapp.generated.resources.stats_section_cumulative
import birdy_bird_scanner.composeapp.generated.resources.stats_section_months
import birdy_bird_scanner.composeapp.generated.resources.stats_section_seasons
import birdy_bird_scanner.composeapp.generated.resources.stats_section_top
import birdy_bird_scanner.composeapp.generated.resources.stats_spring
import birdy_bird_scanner.composeapp.generated.resources.stats_summer
import birdy_bird_scanner.composeapp.generated.resources.stats_title
import birdy_bird_scanner.composeapp.generated.resources.stats_total_observations
import birdy_bird_scanner.composeapp.generated.resources.stats_total_species
import birdy_bird_scanner.composeapp.generated.resources.stats_winter
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.components.BackButton
import se.birdy.app.ui.components.JournalIntro
import se.birdy.app.ui.components.JournalLoading
import se.birdy.app.ui.components.JournalScaffold
import se.birdy.app.ui.components.MicroLabel
import se.birdy.app.ui.components.SectionCard
import se.birdy.app.ui.stats.charts.JournalBarChart
import se.birdy.app.ui.stats.charts.JournalDonutChart
import se.birdy.app.ui.stats.charts.JournalLineChart
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.Brass
import se.birdy.app.ui.theme.BrassInk
import se.birdy.app.ui.theme.BrassLight
import se.birdy.app.ui.theme.BrassText
import se.birdy.app.ui.theme.Hairline
import se.birdy.app.ui.theme.HeroMossMid
import se.birdy.app.ui.theme.InkMuted
import se.birdy.app.ui.theme.MarginaliaInk
import se.birdy.app.ui.theme.TextOnCreme
import se.birdy.app.ui.theme.rememberCaveat
import se.birdy.app.ui.theme.rememberDmSerifDisplay

/**
 * Plan 6b3 T11: Premium-tier Season Statistics page. Shows a snapshot of the
 * current calendar year — month bars, season donut, top-5 species, cumulative
 * new-species line. Locale-aware month abbreviations; the empty state surfaces
 * if the user has no observations from the current year yet.
 *
 * 1.3.0 T12: cards on paper for every chart section and a mossfärgad progress bar per
 * top-species row — see the private composables below for the per-section styling.
 *
 * 1.3.0 T12b: the "PREMIUM" pill moved into [SeasonStatsTopBar] (top-right, see [PremiumBadge]);
 * the top-species section is now its own [SectionCard] ([TopSpeciesCard]); season colors are
 * shared via [SeasonPalette]; section headers are marked `heading()` for TalkBack.
 */
@Composable
fun SeasonStatsScreen(
    viewModel: SeasonStatsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) { viewModel.onEnter() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    JournalScaffold(
        modifier = modifier,
        topBar = { SeasonStatsTopBar(onBack = onBack) },
    ) { padding ->
        when (val s = state) {
            SeasonStatsUiState.Loading -> JournalLoading()
            SeasonStatsUiState.Empty -> EmptyContent(Modifier.padding(padding))
            is SeasonStatsUiState.Loaded -> LoadedContent(s, Modifier.padding(padding))
        }
    }
}

@Composable
private fun SeasonStatsTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BackButton(
            onClick = onBack,
            contentDescription = stringResource(Res.string.stats_back),
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = stringResource(Res.string.stats_title),
            fontFamily = rememberDmSerifDisplay(),
            fontSize = 20.sp,
            color = TextOnCreme,
            modifier = Modifier.weight(1f),
        )
        PremiumBadge()
    }
}

/**
 * Small mässing "PREMIUM" pill, top-right of the top bar, vertically centred with the back
 * button (T12b review fix — was [JournalIntro]'s `trailingContent`, which rendered on its own
 * row below the ornament and looked detached; the mockup puts it on the kicker row instead).
 * Uses the neutral [Res.string.premium_badge] key rather than `premium_teaser_corner` — that
 * key is a teaser CTA reused by other screens, and changing its wording later shouldn't also
 * change this badge's.
 */
@Composable
private fun PremiumBadge() {
    Text(
        text = stringResource(Res.string.premium_badge),
        color = BrassInk,
        fontSize = 9.sp,
        lineHeight = 12.sp,
        maxLines = 1,
        fontWeight = FontWeight.W700,
        letterSpacing = 0.14.em,
        modifier =
            Modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(Brush.linearGradient(listOf(BrassLight, Brass)))
                .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun EmptyContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        JournalIntro(
            label = stringResource(Res.string.stats_empty_eyebrow),
            headline = stringResource(Res.string.stats_empty_headline),
            sub = stringResource(Res.string.stats_empty_sub),
            horizontalPadding = 0,
            topPadding = 0,
        )
    }
}

@Composable
private fun LoadedContent(
    s: SeasonStatsUiState.Loaded,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 40.dp),
    ) {
        item {
            JournalIntro(
                label = stringResource(Res.string.stats_intro_eyebrow),
                headline = stringResource(Res.string.stats_intro_headline),
                sub = stringResource(Res.string.stats_intro_sub),
            )
        }
        item { TotalsRow(s.totalSpeciesThisYear, s.totalObservationsThisYear) }
        item { Spacer(Modifier.height(10.dp)) }
        item { MonthsCard(s.monthBars) }
        item { Spacer(Modifier.height(10.dp)) }
        item { SeasonsCard(s.seasonDonut) }
        item { Spacer(Modifier.height(10.dp)) }
        item { TopSpeciesCard(s.topSpecies) }
        item { Spacer(Modifier.height(10.dp)) }
        item { CumulativeCard(s.cumulativeLine) }
    }
}

@Composable
private fun MonthsCard(bars: List<SeasonStatsUiState.MonthBar>) {
    SectionCard(modifier = Modifier.padding(horizontal = 24.dp)) {
        MicroLabel(
            text = stringResource(Res.string.stats_section_months),
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(10.dp))
        JournalBarChart(
            bars = bars,
            contentDescription = stringResource(Res.string.stats_section_months),
        )
        MonthLabelsRow(bars)
    }
}

@Composable
private fun SeasonsCard(breakdown: SeasonStatsUiState.SeasonBreakdown) {
    SectionCard(modifier = Modifier.padding(horizontal = 24.dp)) {
        MicroLabel(
            text = stringResource(Res.string.stats_section_seasons),
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(10.dp))
        JournalDonutChart(
            breakdown = breakdown,
            contentDescription = stringResource(Res.string.stats_section_seasons),
        )
        SeasonLegend(breakdown)
    }
}

@Composable
private fun TopSpeciesCard(rows: List<SeasonStatsUiState.TopSpeciesRow>) {
    SectionCard(modifier = Modifier.padding(horizontal = 24.dp)) {
        MicroLabel(
            text = stringResource(Res.string.stats_section_top),
            modifier = Modifier.semantics { heading() },
        )
        val maxCount = (rows.maxOfOrNull { it.count } ?: 1).coerceAtLeast(1)
        rows.forEach { row ->
            key(row.qid) {
                TopSpeciesRow(row, maxCount)
            }
        }
    }
}

@Composable
private fun CumulativeCard(points: List<SeasonStatsUiState.CumulativePoint>) {
    SectionCard(modifier = Modifier.padding(horizontal = 24.dp)) {
        MicroLabel(
            text = stringResource(Res.string.stats_section_cumulative),
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(10.dp))
        JournalLineChart(
            points = points,
            contentDescription = stringResource(Res.string.stats_section_cumulative),
        )
    }
}

@Composable
private fun TotalsRow(
    totalSpecies: Int,
    totalObservations: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionCard(modifier = Modifier.weight(1f).fillMaxHeight()) {
            TotalCell(value = totalSpecies, labelRes = Res.string.stats_total_species)
        }
        SectionCard(modifier = Modifier.weight(1f).fillMaxHeight()) {
            TotalCell(value = totalObservations, labelRes = Res.string.stats_total_observations)
        }
    }
}

@Composable
private fun TotalCell(
    value: Int,
    labelRes: StringResource,
) {
    Column {
        Text(
            text = value.toString(),
            fontFamily = rememberDmSerifDisplay(),
            fontSize = 32.sp,
            lineHeight = 36.sp,
            color = AccentCopper,
        )
        MicroLabel(text = stringResource(labelRes), color = InkMuted, showRule = false)
    }
}

@Composable
private fun MonthLabelsRow(bars: List<SeasonStatsUiState.MonthBar>) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        bars.forEach { b ->
            Text(
                text = b.label,
                fontFamily = rememberCaveat(),
                fontSize = 11.sp,
                color = if (b.isCurrent) BrassText else InkMuted,
                fontWeight = if (b.isCurrent) FontWeight.W700 else FontWeight.W400,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Winter/spring/summer/autumn swatches, sourced from the single shared [SeasonPalette] — also
 * [JournalDonutChart]'s own defaults, so the legend and the donut arcs can never drift apart
 * (T12b: previously duplicated here as literal color tokens, guarded only by a comment).
 */
@Composable
private fun SeasonLegend(breakdown: SeasonStatsUiState.SeasonBreakdown) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LegendRow(stringResource(Res.string.stats_winter), breakdown.winter, SeasonPalette.winter)
        LegendRow(stringResource(Res.string.stats_spring), breakdown.spring, SeasonPalette.spring)
        LegendRow(stringResource(Res.string.stats_summer), breakdown.summer, SeasonPalette.summer)
        LegendRow(stringResource(Res.string.stats_autumn), breakdown.autumn, SeasonPalette.autumn)
    }
}

@Composable
private fun LegendRow(
    name: String,
    count: Int,
    color: Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) { drawCircle(color = color) }
        }
        Spacer(Modifier.size(8.dp))
        Text(
            text = name,
            fontFamily = rememberCaveat(),
            fontSize = 15.sp,
            color = MarginaliaInk,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(Res.string.stats_count_format, count),
            fontFamily = rememberCaveat(),
            fontSize = 15.sp,
            color = AccentCopper,
            letterSpacing = 0.04.em,
        )
    }
}

@Composable
private fun TopSpeciesRow(
    row: SeasonStatsUiState.TopSpeciesRow,
    maxCount: Int,
) {
    // T12b: horizontal inset dropped — this row now lives inside TopSpeciesCard's SectionCard,
    // which already provides its own 14dp inner padding (matching every other card's rows).
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = row.nameLocalized,
                fontFamily = rememberDmSerifDisplay(),
                fontSize = 15.sp,
                color = TextOnCreme,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(Res.string.stats_count_format, row.count),
                fontWeight = FontWeight.W600,
                fontSize = 12.sp,
                color = InkMuted,
            )
        }
        Spacer(Modifier.height(6.dp))
        val fraction = (row.count.toFloat() / maxCount.toFloat()).coerceIn(0f, 1f)
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(Hairline),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(fraction)
                        .height(6.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(HeroMossMid),
            )
        }
    }
}
