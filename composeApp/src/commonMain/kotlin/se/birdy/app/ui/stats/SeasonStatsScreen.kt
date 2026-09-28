package se.birdy.app.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
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
        // T12c M3: a user whose only finds this year are "unknown" gets Loaded with an empty
        // topSpecies — TopSpeciesCard would otherwise render as a card containing nothing but
        // its own header. Skip the card AND its trailing spacer so CumulativeCard's own
        // preceding gap (below) reads as the section break instead.
        if (s.topSpecies.isNotEmpty()) {
            item { TopSpeciesCard(s.topSpecies) }
            item { Spacer(Modifier.height(10.dp)) }
        }
        item { CumulativeCard(s.cumulativeLine) }
    }
}

@Composable
private fun MonthsCard(bars: List<SeasonStatsUiState.MonthBar>) {
    SectionCard(modifier = Modifier.padding(horizontal = 24.dp)) {
        MicroLabel(
            text = stringResource(Res.string.stats_section_months),
            // T12c I-A: MicroLabel applies its modifier to a Row that doesn't merge its
            // children, so a plain `semantics { heading() }` here lands on a text-less
            // container TalkBack can't focus — mergeDescendants pulls the child Text's text up
            // onto this same node so the heading actually carries readable content.
            modifier = Modifier.semantics(mergeDescendants = true) { heading() },
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
            // T12c I-A: see the comment on this same pattern in MonthsCard above.
            modifier = Modifier.semantics(mergeDescendants = true) { heading() },
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
            // T12c I-A: see the comment on this same pattern in MonthsCard above.
            modifier = Modifier.semantics(mergeDescendants = true) { heading() },
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
            // T12c I-A: see the comment on this same pattern in MonthsCard above.
            modifier = Modifier.semantics(mergeDescendants = true) { heading() },
        )
        Spacer(Modifier.height(10.dp))
        JournalLineChart(
            points = points,
            contentDescription = stringResource(Res.string.stats_section_cumulative),
        )
    }
}

/**
 * T12e Minor 3: the two totals cells used to each run [MicroLabel]'s own `autoSize` independently
 * — at ≥1.3-1.5x system font scale "ARTER"/"SPECIES" (the shorter label) still fit at 9.5sp while
 * "OBSERVATIONER"/"OBSERVATIONS" (the longer one) had already shrunk, so the two cells rendered
 * their labels at visibly different sizes (same "mixed sizes" the re-review flagged and rejected
 * for the month axis). Decided ONCE here instead, exactly like [MonthLabelsRow]: measure BOTH
 * labels in [MicroLabel]'s own style against one cell's inner width, and use the largest size
 * (9.5sp down to 6sp) where BOTH fit — so the two totals are always the same size as each other.
 */
@Composable
private fun TotalsRow(
    totalSpecies: Int,
    totalObservations: Int,
) {
    val speciesLabel = stringResource(Res.string.stats_total_species).uppercase()
    val observationsLabel = stringResource(Res.string.stats_total_observations).uppercase()
    val measurer = rememberTextMeasurer(cacheSize = TOTALS_LABEL_MEASURER_CACHE_SIZE)
    val density = LocalDensity.current

    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        val spacingPx = with(density) { 10.dp.roundToPx() }
        // SectionCard's own 14dp padding on every side (SectionCard.kt) — not exposed as a
        // constant there since this is the only place outside it that needs to know it.
        val cellInnerPaddingPx = with(density) { (14.dp * 2).roundToPx() }
        val cellInnerWidthPx = (constraints.maxWidth - spacingPx) / 2 - cellInnerPaddingPx

        fun bothFit(fontSize: TextUnit): Boolean {
            val style =
                TextStyle(
                    fontFamily = FontFamily.SansSerif,
                    fontSize = fontSize,
                    fontWeight = FontWeight.W600,
                    letterSpacing = 0.16.em,
                )

            fun widthPx(label: String) = measurer.measure(label, style, maxLines = 1, softWrap = false).size.width
            return widthPx(speciesLabel) <= cellInnerWidthPx && widthPx(observationsLabel) <= cellInnerWidthPx
        }
        val labelFontSize = TOTALS_LABEL_SIZE_STEPS.firstOrNull(::bothFit) ?: TOTALS_LABEL_SIZE_STEPS.last()

        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionCard(modifier = Modifier.weight(1f).fillMaxHeight()) {
                TotalCell(
                    value = totalSpecies,
                    labelRes = Res.string.stats_total_species,
                    labelFontSize = labelFontSize,
                )
            }
            SectionCard(modifier = Modifier.weight(1f).fillMaxHeight()) {
                TotalCell(
                    value = totalObservations,
                    labelRes = Res.string.stats_total_observations,
                    labelFontSize = labelFontSize,
                )
            }
        }
    }
}

/** 9.5sp (unchanged at 1.0x) down to a 6sp floor, same range [MicroLabel]'s old autoSize used. */
private val TOTALS_LABEL_SIZE_STEPS = listOf(9.5.sp, 9.sp, 8.5.sp, 8.sp, 7.5.sp, 7.sp, 6.5.sp, 6.sp)
private const val TOTALS_LABEL_MEASURER_CACHE_SIZE = 24

@Composable
private fun TotalCell(
    value: Int,
    labelRes: StringResource,
    labelFontSize: TextUnit,
) {
    Column {
        Text(
            text = value.toString(),
            fontFamily = rememberDmSerifDisplay(),
            fontSize = 32.sp,
            lineHeight = 36.sp,
            color = AccentCopper,
        )
        MicroLabel(
            text = stringResource(labelRes),
            color = InkMuted,
            showRule = false,
            fontSize = labelFontSize,
        )
    }
}

/**
 * T12d Important 1: the row used to render every month label at a fixed 11sp — at large system
 * font scales on a narrow phone, 3-letter labels (MAR/MAJ/MAY) no longer fit their 1/12th slot
 * and Compose force-splits them mid-word (e.g. "MA" over "R"). Decided ONCE for the whole row
 * (not per label, which would give each month a different size) via a single measure pass:
 * 1) 3-letter labels at today's 11sp if the widest one fits its slot: unchanged from before.
 * 2) else uniform 9.5sp (matches [MicroLabel]'s own kicker size) if that fits.
 * 3) else a one-letter axis ("J F M A M J J A S O N D") — verified in the re-review to still
 *    fit every slot up to a 2.0x system font scale, the largest this app supports.
 * Measuring with the bold (current-month) style variant since it's the widest of the two weights
 * this row ever renders, so the decision never under-estimates the space a label actually needs.
 */
@Composable
private fun MonthLabelsRow(bars: List<SeasonStatsUiState.MonthBar>) {
    // T12e Minor 5: JournalBarChart (the bar row this axis sits under) guards the same way —
    // bars is always 12 items from the ViewModel today, but widestWidthPx below calls
    // bars.maxOf, which throws on an empty list.
    if (bars.isEmpty()) return
    val fontFamily = rememberCaveat()
    // T12e Minor 6: the default cache (8) is smaller than the 12-24 texts (two font sizes ×
    // up to 12 months) this row measures on every composition, e.g. every time the card
    // re-enters a LazyColumn viewport.
    val measurer = rememberTextMeasurer(cacheSize = MONTH_LABEL_MEASURER_CACHE_SIZE)
    BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        val slotCount = bars.size.coerceAtLeast(1)
        val slotPx = constraints.maxWidth / slotCount

        fun widestWidthPx(fontSize: TextUnit): Int {
            val style = TextStyle(fontFamily = fontFamily, fontSize = fontSize, fontWeight = FontWeight.W700)
            return bars.maxOf { b ->
                measurer.measure(text = b.label, style = style, maxLines = 1, softWrap = false).size.width
            }
        }
        val mode =
            when {
                widestWidthPx(MONTH_LABEL_FULL_SIZE) <= slotPx -> MonthLabelMode.FULL
                widestWidthPx(MONTH_LABEL_MEDIUM_SIZE) <= slotPx -> MonthLabelMode.MEDIUM
                else -> MonthLabelMode.INITIAL
            }
        Row(Modifier.fillMaxWidth()) {
            bars.forEach { b ->
                val displayText = if (mode == MonthLabelMode.INITIAL) b.label.take(1) else b.label
                val fontSize = if (mode == MonthLabelMode.MEDIUM) MONTH_LABEL_MEDIUM_SIZE else MONTH_LABEL_FULL_SIZE
                Text(
                    text = displayText,
                    fontFamily = fontFamily,
                    fontSize = fontSize,
                    color = if (b.isCurrent) BrassText else InkMuted,
                    fontWeight = if (b.isCurrent) FontWeight.W700 else FontWeight.W400,
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.Center,
                    modifier =
                        Modifier
                            .weight(1f)
                            .then(
                                // Shortened to one letter on screen: TalkBack should still hear
                                // the 3-letter abbreviation ("JAN"), not "J" — b.label IS that
                                // abbreviation (never the full month name, in either mode; T12e
                                // Minor 4 corrected this comment). clearAndSetSemantics (not a
                                // plain contentDescription) so the node doesn't ALSO carry the
                                // one-letter text and get announced twice.
                                if (mode == MonthLabelMode.INITIAL) {
                                    Modifier.clearAndSetSemantics { contentDescription = b.label }
                                } else {
                                    Modifier
                                },
                            ),
                )
            }
        }
    }
}

private enum class MonthLabelMode { FULL, MEDIUM, INITIAL }

private val MONTH_LABEL_FULL_SIZE = 11.sp
private val MONTH_LABEL_MEDIUM_SIZE = 9.5.sp
private const val MONTH_LABEL_MEASURER_CACHE_SIZE = 24

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
