package se.birdy.app.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import birdy_bird_scanner.composeapp.generated.resources.Res
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
import birdy_bird_scanner.composeapp.generated.resources.stats_winter
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.components.BackButton
import se.birdy.app.ui.components.JournalIntro
import se.birdy.app.ui.components.JournalLoading
import se.birdy.app.ui.components.JournalScaffold
import se.birdy.app.ui.components.MicroLabel
import se.birdy.app.ui.components.OrnamentRule
import se.birdy.app.ui.stats.charts.JournalBarChart
import se.birdy.app.ui.stats.charts.JournalDonutChart
import se.birdy.app.ui.stats.charts.JournalLineChart
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.HeroMossMid
import se.birdy.app.ui.theme.MarginaliaInk
import se.birdy.app.ui.theme.TextOnCreme
import se.birdy.app.ui.theme.rememberCaveat
import se.birdy.app.ui.theme.rememberDmSerifDisplay

private val WinterLegendColor = Color(0xFF1F3A5F)

/**
 * Plan 6b3 T11: Premium-tier Season Statistics page. Shows a snapshot of the
 * current calendar year — month bars, season donut, top-5 species, cumulative
 * new-species line. Locale-aware month abbreviations; the empty state surfaces
 * if the user has no observations from the current year yet.
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
        )
    }
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
        item { SectionLabel(text = stringResource(Res.string.stats_section_months)) }
        item {
            JournalBarChart(
                bars = s.monthBars,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
        item { MonthLabelsRow(s.monthBars) }
        item { OrnamentRule(modifier = Modifier.padding(horizontal = 24.dp)) }
        item { SectionLabel(text = stringResource(Res.string.stats_section_seasons)) }
        item {
            JournalDonutChart(
                breakdown = s.seasonDonut,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
        item { SeasonLegend(s.seasonDonut) }
        item { OrnamentRule(modifier = Modifier.padding(horizontal = 24.dp)) }
        item { SectionLabel(text = stringResource(Res.string.stats_section_top)) }
        items(s.topSpecies) { row -> TopSpeciesRow(row) }
        item { OrnamentRule(modifier = Modifier.padding(horizontal = 24.dp)) }
        item { SectionLabel(text = stringResource(Res.string.stats_section_cumulative)) }
        item {
            JournalLineChart(
                points = s.cumulativeLine,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
        MicroLabel(text)
    }
}

@Composable
private fun TotalsRow(
    totalSpecies: Int,
    totalObservations: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        TotalCell(value = totalSpecies, labelRes = Res.string.stats_section_top)
        TotalCell(value = totalObservations, labelRes = Res.string.stats_section_months)
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
            fontSize = 36.sp,
            color = AccentCopper,
        )
        Text(
            text = stringResource(labelRes),
            fontFamily = rememberCaveat(),
            fontSize = 14.sp,
            color = MarginaliaInk,
        )
    }
}

@Composable
private fun MonthLabelsRow(bars: List<SeasonStatsUiState.MonthBar>) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        bars.forEach { b ->
            Text(
                text = b.label,
                fontFamily = rememberCaveat(),
                fontSize = 11.sp,
                color = if (b.isCurrent) AccentCopper else MarginaliaInk,
                fontWeight = if (b.isCurrent) FontWeight.W700 else FontWeight.W400,
            )
        }
    }
}

@Composable
private fun SeasonLegend(breakdown: SeasonStatsUiState.SeasonBreakdown) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LegendRow(stringResource(Res.string.stats_winter), breakdown.winter, WinterLegendColor)
        LegendRow(stringResource(Res.string.stats_spring), breakdown.spring, HeroMossMid)
        LegendRow(stringResource(Res.string.stats_summer), breakdown.summer, AccentCopper)
        LegendRow(stringResource(Res.string.stats_autumn), breakdown.autumn, MarginaliaInk)
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
private fun TopSpeciesRow(row: SeasonStatsUiState.TopSpeciesRow) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = row.nameLocalized,
            fontFamily = rememberDmSerifDisplay(),
            fontSize = 18.sp,
            color = TextOnCreme,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(Res.string.stats_count_format, row.count),
            fontFamily = rememberCaveat(),
            fontSize = 16.sp,
            color = AccentCopper,
        )
    }
}
