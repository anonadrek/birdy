package se.birdy.app.ui.diary

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.lifelist_empty_caveat_cta
import birdy_bird_scanner.composeapp.generated.resources.lifelist_journal_headline
import birdy_bird_scanner.composeapp.generated.resources.lifelist_journal_headline_anonymous
import birdy_bird_scanner.composeapp.generated.resources.lifelist_journal_label
import birdy_bird_scanner.composeapp.generated.resources.lifelist_journal_sub
import birdy_bird_scanner.composeapp.generated.resources.lifelist_journal_sub_empty
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
import birdy_bird_scanner.composeapp.generated.resources.premium_lifelist_badge
import birdy_bird_scanner.composeapp.generated.resources.premium_lifelist_cta
import birdy_bird_scanner.composeapp.generated.resources.premium_lifelist_preview_caption
import birdy_bird_scanner.composeapp.generated.resources.premium_lifelist_title
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.components.JournalHeadline
import se.birdy.app.ui.components.JournalIntro
import se.birdy.app.ui.components.LockedStatsPreview
import se.birdy.app.ui.components.MiniStamp
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.MarginaliaInk
import se.birdy.app.ui.theme.MatchHigh
import se.birdy.app.ui.theme.MatchLow
import se.birdy.app.ui.theme.MatchMid
import se.birdy.app.ui.theme.OffwhiteWarm
import se.birdy.app.ui.theme.PaperBottom
import se.birdy.app.ui.theme.TextOnCreme
import se.birdy.app.ui.theme.paperBackground
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
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(containerColor = Color.Transparent) { padding ->
        Box(modifier = Modifier.fillMaxSize().paperBackground().padding(padding)) {
            when (val s = state) {
                LifelistUiState.Loading ->
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                LifelistUiState.Empty -> EmptyLifelist(onScanCtaClick = onScanCtaClick)
                is LifelistUiState.Loaded ->
                    LoadedLifelist(
                        state = s,
                        onObservationClick = onObservationClick,
                        onStat3Toggle = viewModel::onStat3Toggle,
                        onSortToggle = viewModel::onSortToggle,
                        onPremiumClick = onPremiumClick,
                    )
            }
        }
    }
}

// ─── Empty state ─────────────────────────────────────────────────────────────

@Composable
private fun EmptyLifelist(onScanCtaClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        JournalIntro(
            label = stringResource(Res.string.lifelist_journal_label),
            headline = stringResource(Res.string.lifelist_journal_headline_anonymous),
            sub = stringResource(Res.string.lifelist_journal_sub_empty),
        )
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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

@Composable
private fun LoadedLifelist(
    state: LifelistUiState.Loaded,
    onObservationClick: (id: String) -> Unit,
    onStat3Toggle: () -> Unit,
    onSortToggle: () -> Unit,
    onPremiumClick: () -> Unit,
) {
    val now = remember { Clock.System.now() }
    val labelStat1 = stringResource(Res.string.lifelist_stat_species)
    val labelStat2 = stringResource(Res.string.lifelist_stat_stamps)
    val labelStat3 = labelForStat3(state.stat3.kind)

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

        items(state.rows, key = { it.observation.id }) { row ->
            LifelistRowComposable(
                row = row,
                now = now,
                onClick = { onObservationClick(row.observation.id) },
            )
        }

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

        item { Spacer(Modifier.height(16.dp)) }
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
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
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
        MiniStamp(number = row.observation.stampNumber)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.species?.name ?: row.observation.speciesId,
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
