package se.birdy.app.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.months_short_uppercase
import birdy_bird_scanner.composeapp.generated.resources.premium_badge
import birdy_bird_scanner.composeapp.generated.resources.stats_autumn
import birdy_bird_scanner.composeapp.generated.resources.stats_back
import birdy_bird_scanner.composeapp.generated.resources.stats_current_month_legend
import birdy_bird_scanner.composeapp.generated.resources.stats_empty_headline
import birdy_bird_scanner.composeapp.generated.resources.stats_empty_sub
import birdy_bird_scanner.composeapp.generated.resources.stats_intro_eyebrow_year
import birdy_bird_scanner.composeapp.generated.resources.stats_intro_headline
import birdy_bird_scanner.composeapp.generated.resources.stats_label_finds_description
import birdy_bird_scanner.composeapp.generated.resources.stats_months_full
import birdy_bird_scanner.composeapp.generated.resources.stats_ring_description
import birdy_bird_scanner.composeapp.generated.resources.stats_ring_finds_label
import birdy_bird_scanner.composeapp.generated.resources.stats_ring_species
import birdy_bird_scanner.composeapp.generated.resources.stats_ring_total_description
import birdy_bird_scanner.composeapp.generated.resources.stats_spring
import birdy_bird_scanner.composeapp.generated.resources.stats_summary_best_month
import birdy_bird_scanner.composeapp.generated.resources.stats_summary_best_month_current
import birdy_bird_scanner.composeapp.generated.resources.stats_summary_first_find
import birdy_bird_scanner.composeapp.generated.resources.stats_summary_total
import birdy_bird_scanner.composeapp.generated.resources.stats_summer
import birdy_bird_scanner.composeapp.generated.resources.stats_title
import birdy_bird_scanner.composeapp.generated.resources.stats_winter
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.components.BackButton
import se.birdy.app.ui.components.JournalHeadline
import se.birdy.app.ui.components.JournalLoading
import se.birdy.app.ui.components.JournalScaffold
import se.birdy.app.ui.components.JournalSubLine
import se.birdy.app.ui.components.MicroLabel
import se.birdy.app.ui.stats.charts.RingCenterText
import se.birdy.app.ui.stats.charts.YearRing
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.Brass
import se.birdy.app.ui.theme.BrassInk
import se.birdy.app.ui.theme.BrassLight
import se.birdy.app.ui.theme.BrassText
import se.birdy.app.ui.theme.InkMuted
import se.birdy.app.ui.theme.MarginaliaInk
import se.birdy.app.ui.theme.TextOnCreme
import se.birdy.app.ui.theme.rememberCaveat
import se.birdy.app.ui.theme.rememberDmSerifDisplay

/**
 * The ring's square, letters included. 300dp on a w411 phone gives the busiest month's circle the
 * mockup's share of the screen width (≈ 62 %); narrower phones get the full content width.
 */
private val RingMaxSize = 300.dp
private const val MONTHS_IN_YEAR = 12

/**
 * Plan 6b3 T11: Premium-tier Season Statistics page, a snapshot of the current calendar year.
 *
 * Release 1.3.0 Task 7c (Albin's choice 2026-10-06, design option B, "the year ring and a journal
 * of firsts"): a sentence computed from the year under the headline ([StatsSummary]), the year as a
 * ring ([YearRing]: January at the top, total in the middle, brass only for the current month) with
 * the season sums under it, a timeline of this year's first find per species
 * ([firstSightingsTimeline]) and the three most seen species as photo seals ([TopSpeciesSeals]).
 * The bar chart, the season donut (and its navy) and the cumulative line are gone. A year without
 * finds still draws the empty ring, so the empty screen reads as a blank page of the same journal.
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
            is SeasonStatsUiState.Empty -> EmptyContent(s, Modifier.padding(padding))
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
        // T12c M2: BasicText + autoSize instead of a fixed 20sp Text — with the PREMIUM badge
        // eating into the row's width, "Säsongsstatistik" wrapped mid-word at w360 under a ~1.4×
        // system font scale. Same StepBased pattern as the Premium screen's price line
        // (ui/premium/PremiumScreen.kt).
        BasicText(
            text = stringResource(Res.string.stats_title),
            style = TextStyle(fontFamily = rememberDmSerifDisplay(), color = TextOnCreme, fontSize = 20.sp),
            maxLines = 1,
            // T12d Minor: at a 2.0x system font scale on a w360dp phone, even the 14sp floor
            // needs more room than the badge leaves — StepBased has nowhere further to shrink,
            // so the title hard-clipped instead of ellipsizing. A lower minFontSize (11sp) buys
            // a bit more room before that point, and `overflow = Ellipsis` gives a graceful
            // last resort instead of a clipped word once autoSize truly runs out of space.
            overflow = TextOverflow.Ellipsis,
            autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = 20.sp),
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        PremiumBadge()
    }
}

/**
 * Small mässing "PREMIUM" pill, top-right of the top bar, vertically centred with the back
 * button (T12b review fix — was the intro's `trailingContent`, which rendered on its own
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
private fun EmptyContent(
    s: SeasonStatsUiState.Empty,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 40.dp),
    ) {
        item {
            StatsIntro(
                year = s.year,
                headline = stringResource(Res.string.stats_empty_headline),
                sentence = stringResource(Res.string.stats_empty_sub),
            )
        }
        yearRingSection(
            monthCounts = List(MONTHS_IN_YEAR) { 0 },
            currentMonth = s.currentMonth,
            bestMonth = null,
            year = s.year,
            speciesCount = 0,
            seasons = null,
        )
    }
}

@Composable
private fun LoadedContent(
    s: SeasonStatsUiState.Loaded,
    modifier: Modifier = Modifier,
) {
    val monthNames = fullMonthNames()
    val summary = statsSummaryFor(s.totalObservationsThisYear, s.bestMonth, s.currentMonth)
    val sentence = summarySentence(summary, monthNames)
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 40.dp),
    ) {
        item {
            StatsIntro(year = s.year, headline = stringResource(Res.string.stats_intro_headline), sentence = sentence)
        }
        yearRingSection(
            monthCounts = s.monthBars.sortedBy { it.month }.map { it.observationCount },
            currentMonth = s.currentMonth,
            bestMonth = s.bestMonth,
            year = s.year,
            speciesCount = s.totalSpeciesThisYear,
            seasons = s.seasons,
        )
        firstSightingsTimeline(s.firstSightings)
        // A year whose only finds are "unknown" has no species to show: no empty cards either.
        if (s.topSpecies.isNotEmpty()) {
            item { Spacer(Modifier.height(12.dp)) }
            item(key = "top-species") { TopSpeciesSeals(s.topSpecies) }
        }
    }
}

/** Kicker with the year, the headline and the sentence computed from the year (no ornament, as in the mockup). */
@Composable
private fun StatsIntro(
    year: Int,
    headline: String,
    sentence: String,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp)) {
        MicroLabel(stringResource(Res.string.stats_intro_eyebrow_year, year))
        Spacer(Modifier.height(6.dp))
        JournalHeadline(headline)
        Spacer(Modifier.height(2.dp))
        JournalSubLine(sentence)
    }
}

@Composable
private fun summarySentence(
    summary: StatsSummary,
    monthNames: List<String>,
): String =
    when (summary) {
        is StatsSummary.FirstFind ->
            stringResource(Res.string.stats_summary_first_find, monthNames[summary.month - 1])
        is StatsSummary.BestMonth -> {
            val month = monthNames[summary.month - 1].replaceFirstChar { it.titlecase() }
            val res =
                if (summary.isCurrentMonth) {
                    Res.plurals.stats_summary_best_month_current
                } else {
                    Res.plurals.stats_summary_best_month
                }
            pluralStringResource(res, summary.totalFinds, summary.totalFinds, month)
        }
        is StatsSummary.TotalOnly ->
            pluralStringResource(Res.plurals.stats_summary_total, summary.totalFinds, summary.totalFinds)
    }

/** Full month names, January first, in the app's language ("januari" / "January"). */
@Composable
internal fun fullMonthNames(): List<String> = stringArrayResource(Res.array.stats_months_full)

@Suppress("LongParameterList") // the ring's data, passed straight on to the items below
private fun LazyListScope.yearRingSection(
    monthCounts: List<Int>,
    currentMonth: Int?,
    bestMonth: Int?,
    year: Int,
    speciesCount: Int,
    seasons: SeasonStatsUiState.SeasonBreakdown?,
) {
    item(key = "year-ring") {
        YearRingBlock(monthCounts, currentMonth, bestMonth, year, speciesCount)
    }
    if (seasons != null) item(key = "seasons") { SeasonsLine(seasons) }
    if (currentMonth != null) item(key = "current-month") { CurrentMonthLegend(currentMonth) }
}

@Composable
private fun YearRingBlock(
    monthCounts: List<Int>,
    currentMonth: Int?,
    bestMonth: Int?,
    year: Int,
    speciesCount: Int,
) {
    val total = monthCounts.sum()
    val letters = stringArrayResource(Res.array.months_short_uppercase).map { it.take(1) }
    val description = ringDescription(monthCounts, currentMonth, year, speciesCount)
    val centerText =
        RingCenterText(
            total = total.toString(),
            label = pluralStringResource(Res.plurals.stats_ring_finds_label, total),
            note =
                if (speciesCount > 0) {
                    pluralStringResource(Res.plurals.stats_ring_species, speciesCount, speciesCount)
                } else {
                    null
                },
        )
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        YearRing(
            monthCounts = monthCounts,
            currentMonth = currentMonth,
            bestMonth = bestMonth,
            monthLetters = letters,
            centerText = centerText,
            contentDescription = description,
            modifier = Modifier.size(min(maxWidth, RingMaxSize)),
        )
    }
}

/**
 * The ring's text for TalkBack, everything it shows in plain words: "Årsring för 2026 med fynd per
 * månad, januari överst. 15 fynd totalt. 5 arter. januari: 1 fynd. … oktober, pågår: 2 fynd. …"
 */
@Composable
private fun ringDescription(
    monthCounts: List<Int>,
    currentMonth: Int?,
    year: Int,
    speciesCount: Int,
): String {
    val monthNames = fullMonthNames()
    val total = monthCounts.sum()
    val parts = mutableListOf(stringResource(Res.string.stats_ring_description, year))
    parts += pluralStringResource(Res.plurals.stats_ring_total_description, total, total)
    if (speciesCount > 0) parts += pluralStringResource(Res.plurals.stats_ring_species, speciesCount, speciesCount)
    val currentLabel = currentMonth?.let { stringResource(Res.string.stats_current_month_legend, monthNames[it - 1]) }
    monthCounts.forEachIndexed { index, count ->
        val name = if (index + 1 == currentMonth && currentLabel != null) currentLabel else monthNames[index]
        parts += pluralStringResource(Res.plurals.stats_label_finds_description, count, name, count)
    }
    return parts.joinToString(separator = ". ")
}

/** "vinter 2   vår 7   sommar 4   höst 2": the season sums, named in words (no season colors). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeasonsLine(seasons: SeasonStatsUiState.SeasonBreakdown) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
    ) {
        SeasonSum(stringResource(Res.string.stats_winter), seasons.winter)
        SeasonSum(stringResource(Res.string.stats_spring), seasons.spring)
        SeasonSum(stringResource(Res.string.stats_summer), seasons.summer)
        SeasonSum(stringResource(Res.string.stats_autumn), seasons.autumn)
    }
}

@Composable
private fun SeasonSum(
    name: String,
    count: Int,
) {
    val description = pluralStringResource(Res.plurals.stats_label_finds_description, count, name, count)
    Text(
        text =
            buildAnnotatedString {
                append(name.lowercase())
                append(" ")
                withStyle(SpanStyle(fontWeight = FontWeight.W700, color = AccentCopper)) { append(count.toString()) }
            },
        fontFamily = rememberCaveat(),
        fontSize = 17.sp,
        color = MarginaliaInk,
        modifier = Modifier.semantics { contentDescription = description },
    )
}

/** The key to the one brass segment: a brass swatch and "oktober, pågår". */
@Composable
private fun CurrentMonthLegend(currentMonth: Int) {
    val swatchShape = RoundedCornerShape(2.dp)
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 2.dp, bottom = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(10.dp)
                    .clip(swatchShape)
                    .background(Brass)
                    .border(1.dp, BrassText, swatchShape),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(Res.string.stats_current_month_legend, fullMonthNames()[currentMonth - 1]),
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.W500,
            fontSize = 12.sp,
            color = InkMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}
