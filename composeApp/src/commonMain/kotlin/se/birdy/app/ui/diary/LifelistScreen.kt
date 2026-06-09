package se.birdy.app.ui.diary

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.lifelist_empty_caveat_cta
import birdy_bird_scanner.composeapp.generated.resources.lifelist_empty_marginalia
import birdy_bird_scanner.composeapp.generated.resources.lifelist_empty_stamp_name
import birdy_bird_scanner.composeapp.generated.resources.lifelist_journal_headline
import birdy_bird_scanner.composeapp.generated.resources.lifelist_journal_headline_anonymous
import birdy_bird_scanner.composeapp.generated.resources.lifelist_journal_label
import birdy_bird_scanner.composeapp.generated.resources.lifelist_journal_sub
import birdy_bird_scanner.composeapp.generated.resources.lifelist_journal_sub_empty
import birdy_bird_scanner.composeapp.generated.resources.lifelist_month_header
import birdy_bird_scanner.composeapp.generated.resources.lifelist_relative_days
import birdy_bird_scanner.composeapp.generated.resources.lifelist_relative_hours
import birdy_bird_scanner.composeapp.generated.resources.lifelist_relative_just_now
import birdy_bird_scanner.composeapp.generated.resources.lifelist_relative_minutes
import birdy_bird_scanner.composeapp.generated.resources.lifelist_section_recent
import birdy_bird_scanner.composeapp.generated.resources.lifelist_sort_recent
import birdy_bird_scanner.composeapp.generated.resources.lifelist_sort_species
import birdy_bird_scanner.composeapp.generated.resources.lifelist_sort_stamp
import birdy_bird_scanner.composeapp.generated.resources.lifelist_stat_longest
import birdy_bird_scanner.composeapp.generated.resources.lifelist_stat_month
import birdy_bird_scanner.composeapp.generated.resources.lifelist_stat_species
import birdy_bird_scanner.composeapp.generated.resources.lifelist_stat_stamps
import birdy_bird_scanner.composeapp.generated.resources.lifelist_stat_streak
import birdy_bird_scanner.composeapp.generated.resources.lifelist_stat_year
import birdy_bird_scanner.composeapp.generated.resources.months_short_uppercase
import birdy_bird_scanner.composeapp.generated.resources.premium_lifelist_badge
import birdy_bird_scanner.composeapp.generated.resources.premium_lifelist_cta
import birdy_bird_scanner.composeapp.generated.resources.premium_lifelist_preview_caption
import birdy_bird_scanner.composeapp.generated.resources.premium_lifelist_title
import birdy_bird_scanner.composeapp.generated.resources.recap_eyebrow_fmt
import birdy_bird_scanner.composeapp.generated.resources.recap_lifelist_entry_title
import birdy_bird_scanner.composeapp.generated.resources.recap_summary_active_fmt
import birdy_bird_scanner.composeapp.generated.resources.unknown_species_label
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.components.JournalHeadline
import se.birdy.app.ui.components.JournalIntro
import se.birdy.app.ui.components.JournalLoading
import se.birdy.app.ui.components.JournalScaffold
import se.birdy.app.ui.components.JournalSubLine
import se.birdy.app.ui.components.LockedStatsPreview
import se.birdy.app.ui.components.MiniStamp
import se.birdy.app.ui.components.StampSeal
import se.birdy.app.ui.components.StampSealState
import se.birdy.app.ui.components.shimmerBorder
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.HeroMossLight
import se.birdy.app.ui.theme.HeroMossMid
import se.birdy.app.ui.theme.MarginaliaInk
import se.birdy.app.ui.theme.MatchHigh
import se.birdy.app.ui.theme.MatchLow
import se.birdy.app.ui.theme.MatchMid
import se.birdy.app.ui.theme.OffwhiteWarm
import se.birdy.app.ui.theme.PaperBottom
import se.birdy.app.ui.theme.TextOnCreme
import se.birdy.app.ui.theme.rememberCaveat
import se.birdy.app.ui.theme.rememberDmSerifDisplay
import se.birdy.datastore.LifelistSort
import se.birdy.datastore.LifelistStat3Choice

@Composable
fun LifelistScreen(
    viewModel: LifelistViewModel,
    onObservationClick: (id: String) -> Unit,
    onScanCtaClick: () -> Unit,
    onPremiumClick: () -> Unit,
    showPremiumTeaser: Boolean = true,
    livePreviewState: se.birdy.app.ui.stats.SeasonStatsUiState.Loaded? = null,
    onSeasonStatsClick: () -> Unit = {},
    onRecapClick: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    JournalScaffold { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                LifelistUiState.Loading -> JournalLoading()
                LifelistUiState.Empty -> EmptyLifelist(onScanCtaClick = onScanCtaClick)
                is LifelistUiState.Loaded ->
                    LoadedLifelist(
                        state = s,
                        onObservationClick = onObservationClick,
                        onStat3Toggle = viewModel::onStat3Toggle,
                        onSortToggle = viewModel::onSortToggle,
                        onPremiumClick = onPremiumClick,
                        showPremiumTeaser = showPremiumTeaser,
                        livePreviewState = livePreviewState,
                        onSeasonStatsClick = onSeasonStatsClick,
                        onRecapClick = onRecapClick,
                    )
            }
        }
    }
}

// ─── Empty state ─────────────────────────────────────────────────────────────

@Composable
private fun EmptyLifelist(onScanCtaClick: () -> Unit) {
    val caveat = rememberCaveat()
    Column(modifier = Modifier.fillMaxSize()) {
        JournalIntro(
            label = stringResource(Res.string.lifelist_journal_label),
            headline = stringResource(Res.string.lifelist_journal_headline_anonymous),
            sub = stringResource(Res.string.lifelist_journal_sub_empty),
        )
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            StampSeal(
                state = StampSealState.Locked(name = stringResource(Res.string.lifelist_empty_stamp_name)),
                size = 88.dp,
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(Res.string.lifelist_empty_marginalia),
                color = MarginaliaInk,
                fontFamily = caveat,
                fontSize = 16.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = onScanCtaClick,
                colors = ButtonDefaults.buttonColors(containerColor = AccentCopper, contentColor = OffwhiteWarm),
                shape = RoundedCornerShape(50),
            ) {
                JournalHeadline(
                    text = stringResource(Res.string.lifelist_empty_caveat_cta),
                    fontSize = 14.sp,
                    plainColor = OffwhiteWarm,
                    accentColor = OffwhiteWarm,
                )
            }
        }
    }
}

// ─── Loaded state ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LoadedLifelist(
    state: LifelistUiState.Loaded,
    onObservationClick: (id: String) -> Unit,
    onStat3Toggle: () -> Unit,
    onSortToggle: () -> Unit,
    onPremiumClick: () -> Unit,
    showPremiumTeaser: Boolean,
    livePreviewState: se.birdy.app.ui.stats.SeasonStatsUiState.Loaded? = null,
    onSeasonStatsClick: () -> Unit = {},
    onRecapClick: () -> Unit = {},
) {
    val now = remember { Clock.System.now() }
    val labelStat1 = stringResource(Res.string.lifelist_stat_species)
    val labelStat2 = stringResource(Res.string.lifelist_stat_stamps)
    val labelStat3 = labelForStat3(state.stat3.kind)
    val months = stringArrayResource(Res.array.months_short_uppercase)
    val headerFmt = stringResource(Res.string.lifelist_month_header)
    val zone = remember { TimeZone.currentSystemDefault() }
    val grouped =
        remember(state.rows, state.sort) {
            if (state.sort != LifelistSort.RECENT) {
                emptyMap()
            } else {
                state.rows.groupBy { row ->
                    val date =
                        row.observation.savedAt
                            .toLocalDateTime(zone)
                            .date
                    date.year to date.monthNumber
                }
            }
        }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Column {
                JournalIntro(
                    label = stringResource(Res.string.lifelist_journal_label),
                    headline =
                        stringResource(
                            Res.string.lifelist_journal_headline,
                            state.userName.ifEmpty { "Min" },
                        ),
                    sub =
                        stringResource(
                            Res.string.lifelist_journal_sub,
                            state.daysActive.toString(),
                            state.speciesCount.toString(),
                        ),
                )
                StatRow(
                    stat1 = StatItem(labelStat1, state.speciesCount.toString()),
                    stat2 = StatItem(labelStat2, state.stampsCount.toString()),
                    stat3 = StatItem(labelStat3, state.stat3.value.toString()),
                    onStat3Click = onStat3Toggle,
                )
            }
        }

        item {
            RecapEntryCard(preview = state.recapPreview, onClick = onRecapClick)
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.lifelist_section_recent, state.stampsCount.toString()).uppercase(),
                    color = MarginaliaInk,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.W700,
                    letterSpacing = 0.22.em,
                )
                SortChip(sort = state.sort, onClick = onSortToggle)
            }
        }

        if (state.sort == LifelistSort.RECENT) {
            grouped.forEach { (yearMonth, rows) ->
                val (year, month) = yearMonth
                val monthLabel = months.getOrNull(month - 1) ?: month.toString()
                stickyHeader(key = "month-$year-$month") {
                    MonthHeader(
                        text =
                            headerFmt
                                .replace("%1\$s", "$monthLabel $year")
                                .replace("%2\$s", rows.size.toString()),
                    )
                }
                items(rows, key = { it.observation.id }) { row ->
                    LifelistRowComposable(
                        row = row,
                        now = now,
                        onClick = { onObservationClick(row.observation.id) },
                    )
                }
            }
        } else {
            items(state.rows, key = { it.observation.id }) { row ->
                LifelistRowComposable(
                    row = row,
                    now = now,
                    onClick = { onObservationClick(row.observation.id) },
                )
            }
        }

        if (showPremiumTeaser) {
            item {
                Spacer(Modifier.height(20.dp))
                Text(
                    text = stringResource(Res.string.premium_lifelist_title),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.W600,
                    letterSpacing = 0.16.em,
                    color = MarginaliaInk,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
                )
                Spacer(Modifier.height(8.dp))
                LockedStatsPreview(
                    title = stringResource(Res.string.premium_lifelist_preview_caption),
                    overlayCta = stringResource(Res.string.premium_lifelist_cta),
                    overlayBadge = stringResource(Res.string.premium_lifelist_badge),
                    onClick = onPremiumClick,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        } else if (livePreviewState != null) {
            item {
                Spacer(Modifier.height(20.dp))
                Text(
                    text = stringResource(Res.string.premium_lifelist_title),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.W600,
                    letterSpacing = 0.16.em,
                    color = MarginaliaInk,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
                )
                Spacer(Modifier.height(8.dp))
                se.birdy.app.ui.stats.LiveStatsPreview(
                    state = livePreviewState,
                    onOpen = onSeasonStatsClick,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ─── Recap entry card ─────────────────────────────────────────────────────────

@Composable
private fun RecapEntryCard(
    preview: RecapPreview,
    onClick: () -> Unit,
) {
    val serif = rememberDmSerifDisplay()
    val caveat = rememberCaveat()
    val photos = preview.findPhotoPaths
    var index by remember(photos) { mutableStateOf(0) }
    if (photos.size > 1) {
        LaunchedEffect(photos) {
            while (true) {
                delay(3500)
                index = (index + 1) % photos.size
            }
        }
    }
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.verticalGradient(listOf(HeroMossLight, HeroMossMid)))
                .clickable(onClick = onClick)
                .shimmerBorder(durationMillis = 7000, cornerRadius = 14.dp, strokeWidth = 3.dp, alpha = 0.85f, bandFraction = 0.3f),
    ) {
        if (photos.isNotEmpty()) {
            Crossfade(
                targetState = index.coerceIn(0, photos.lastIndex),
                animationSpec = tween(900),
                modifier = Modifier.matchParentSize(),
                label = "recapBg",
            ) { i ->
                AsyncImage(
                    model = "file://${photos[i]}",
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().blur(6.dp),
                )
            }
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.35f),
                                    Color.Black.copy(alpha = 0.55f),
                                ),
                            ),
                        ),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(AccentCopper),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = preview.isoWeek.toString(),
                    color = Color.White,
                    fontFamily = caveat,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.recap_eyebrow_fmt, preview.isoWeek.toString()).uppercase(),
                    color = AccentCopper,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.W700,
                    letterSpacing = 1.6.sp,
                )
                Text(
                    text = stringResource(Res.string.recap_lifelist_entry_title),
                    color = Color.White,
                    fontFamily = serif,
                    fontStyle = FontStyle.Italic,
                    fontSize = 20.sp,
                )
                if (preview.findCount > 0) {
                    Text(
                        text = stringResource(Res.string.recap_summary_active_fmt, preview.findCount.toString()),
                        color = Color.White.copy(alpha = 0.85f),
                        fontFamily = caveat,
                        fontSize = 14.sp,
                    )
                }
            }
            Text(
                text = "›",
                color = Color.White,
                fontFamily = serif,
                fontStyle = FontStyle.Italic,
                fontSize = 22.sp,
            )
        }
    }
}

@Composable
private fun MonthHeader(text: String) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(PaperBottom.copy(alpha = 0.85f))
                .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        JournalSubLine(text = text)
    }
}

// ─── Stat row ─────────────────────────────────────────────────────────────────

private data class StatItem(
    val label: String,
    val value: String,
)

@Composable
private fun StatRow(
    stat1: StatItem,
    stat2: StatItem,
    stat3: StatItem,
    onStat3Click: () -> Unit,
) {
    val serif = rememberDmSerifDisplay()
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatColumn(stat = stat1)
        StatSeparator(serif)
        StatColumn(stat = stat2)
        StatSeparator(serif)
        StatColumn(stat = stat3, onClick = onStat3Click)
    }
}

@Composable
private fun StatColumn(
    stat: StatItem,
    onClick: (() -> Unit)? = null,
) {
    val serif = rememberDmSerifDisplay()
    Column(
        modifier =
            if (onClick != null) {
                Modifier.clickable(onClick = onClick).semantics(mergeDescendants = true) { role = Role.Button }
            } else {
                Modifier
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stat.value,
            color = AccentCopper,
            fontFamily = serif,
            fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.Normal,
            fontSize = 26.sp,
        )
        Text(
            text = stat.label.uppercase(),
            color = MarginaliaInk,
            fontSize = 9.sp,
            fontWeight = FontWeight.W700,
            letterSpacing = 0.22.em,
        )
    }
}

@Composable
private fun StatSeparator(serif: FontFamily) {
    Text(
        text = "·",
        color = AccentCopper.copy(alpha = 0.5f),
        fontFamily = serif,
        fontStyle = FontStyle.Italic,
        fontSize = 22.sp,
    )
}

// ─── Sort chip ────────────────────────────────────────────────────────────────

@Composable
private fun SortChip(
    sort: LifelistSort,
    onClick: () -> Unit,
) {
    val label =
        when (sort) {
            LifelistSort.RECENT -> stringResource(Res.string.lifelist_sort_recent)
            LifelistSort.STAMP_NUMBER -> stringResource(Res.string.lifelist_sort_stamp)
            LifelistSort.SPECIES -> stringResource(Res.string.lifelist_sort_species)
        }
    val serif = rememberDmSerifDisplay()
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(50))
                .background(PaperBottom.copy(alpha = 0.6f))
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            color = AccentCopper,
            fontFamily = serif,
            fontStyle = FontStyle.Italic,
            fontSize = 11.sp,
        )
    }
}

// ─── Stamp row ────────────────────────────────────────────────────────────────

@Composable
private fun LifelistRowComposable(
    row: LifelistRow,
    now: Instant,
    onClick: () -> Unit,
) {
    val serif = rememberDmSerifDisplay()
    val caveat = rememberCaveat()
    val confidencePct = (row.observation.confidence * 100f).toInt()
    val matchColor =
        when {
            confidencePct >= 80 -> MatchHigh
            confidencePct >= 60 -> MatchMid
            else -> MatchLow
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.4f))
                .border(1.dp, AccentCopper.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MiniStamp(number = row.observation.stampNumber, photoPath = row.observation.photoPath)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            val unknownLabel = stringResource(Res.string.unknown_species_label)
            Text(
                text = row.species?.name ?: row.observation.speciesId ?: unknownLabel,
                color = TextOnCreme,
                fontFamily = serif,
                fontStyle = FontStyle.Italic,
                fontSize = 16.sp,
            )
            Text(
                text = "${row.species?.scientificName ?: ""} · ${relativeTime(row.observation.savedAt, now)}",
                color = MarginaliaInk.copy(alpha = 0.7f),
                fontSize = 11.sp,
            )
        }
        Text(
            text = "$confidencePct%",
            color = matchColor,
            fontFamily = caveat,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
        )
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

@Composable
private fun labelForStat3(kind: LifelistStat3Choice): String =
    stringResource(
        when (kind) {
            LifelistStat3Choice.STREAK -> Res.string.lifelist_stat_streak
            LifelistStat3Choice.SPECIES_THIS_YEAR -> Res.string.lifelist_stat_year
            LifelistStat3Choice.SPECIES_THIS_MONTH -> Res.string.lifelist_stat_month
            LifelistStat3Choice.LONGEST_STREAK -> Res.string.lifelist_stat_longest
        },
    )

@Composable
private fun relativeTime(
    instant: Instant,
    now: Instant = Clock.System.now(),
): String {
    val diffMs = now.toEpochMilliseconds() - instant.toEpochMilliseconds()
    val diffMin = diffMs / 60_000L
    val diffH = diffMs / 3_600_000L
    val diffD = diffMs / 86_400_000L
    return when {
        diffMin < 2 -> stringResource(Res.string.lifelist_relative_just_now)
        diffH < 1 -> stringResource(Res.string.lifelist_relative_minutes, diffMin.toString())
        diffD < 1 -> stringResource(Res.string.lifelist_relative_hours, diffH.toString())
        else -> stringResource(Res.string.lifelist_relative_days, diffD.toString())
    }
}
