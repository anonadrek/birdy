package se.birdy.app.ui.diary

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.lifelist_breadcrumb
import birdy_bird_scanner.composeapp.generated.resources.lifelist_empty_cta
import birdy_bird_scanner.composeapp.generated.resources.lifelist_headline_fallback
import birdy_bird_scanner.composeapp.generated.resources.lifelist_headline_format
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
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.components.CircularThumb
import se.birdy.app.ui.components.StampNumberBadge
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.AccentCopperLight
import se.birdy.app.ui.theme.HeroZone
import se.birdy.app.ui.theme.ItalicMixedText
import se.birdy.app.ui.theme.LabelOnCreme
import se.birdy.app.ui.theme.MatchHigh
import se.birdy.app.ui.theme.MatchLow
import se.birdy.app.ui.theme.MatchMid
import se.birdy.app.ui.theme.MossCreme
import se.birdy.app.ui.theme.OffwhiteWarm
import se.birdy.app.ui.theme.SandCreme
import se.birdy.app.ui.theme.TextOnCreme
import se.birdy.datastore.LifelistSort
import se.birdy.datastore.LifelistStat3Choice

@Composable
fun LifelistScreen(
    viewModel: LifelistViewModel,
    onObservationClick: (id: String) -> Unit,
    onScanCtaClick: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(containerColor = MossCreme) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                LifelistUiState.Loading ->
                    Box(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                LifelistUiState.Empty ->
                    EmptyLifelist(onScanCtaClick = onScanCtaClick)
                is LifelistUiState.Loaded ->
                    LoadedLifelist(
                        state = s,
                        onObservationClick = onObservationClick,
                        onStat3Toggle = viewModel::onStat3Toggle,
                        onSortToggle = viewModel::onSortToggle,
                    )
            }
        }
    }
}

// ─── Empty state ─────────────────────────────────────────────────────────────

@Composable
private fun EmptyLifelist(onScanCtaClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        HeroZone {
            Column {
                BreadcrumbLabel()
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(Res.string.lifelist_headline_fallback),
                    color = OffwhiteWarm,
                    fontFamily = FontFamily.Serif,
                    fontStyle = FontStyle.Italic,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.W700,
                )
            }
        }
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Button(
                onClick = onScanCtaClick,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = AccentCopper,
                        contentColor = OffwhiteWarm,
                    ),
                shape = RoundedCornerShape(50),
            ) {
                Text(
                    text = stringResource(Res.string.lifelist_empty_cta),
                    fontWeight = FontWeight.W600,
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
) {
    val now = remember { Clock.System.now() }
    val labelStat1 = stringResource(Res.string.lifelist_stat_species)
    val labelStat2 = stringResource(Res.string.lifelist_stat_stamps)
    val labelStat3 = labelForStat3(state.stat3.kind)

    val stats =
        listOf(
            StatItem(labelStat1, state.speciesCount.toString()),
            StatItem(labelStat2, state.stampsCount.toString()),
            StatItem(labelStat3, state.stat3.value.toString()),
        )

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            HeroZone {
                Column {
                    BreadcrumbLabel()
                    Spacer(Modifier.height(8.dp))
                    LifelistHeadline(name = state.userName)
                    Spacer(Modifier.height(20.dp))
                    StatRow(stats = stats, onStat3Click = onStat3Toggle)
                }
            }
        }

        item {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.lifelist_section_recent, state.stampsCount.toString()),
                    color = LabelOnCreme,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.W600,
                    letterSpacing = 0.18.em,
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

        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ─── Hero internals ───────────────────────────────────────────────────────────

@Composable
private fun BreadcrumbLabel() {
    Text(
        text = stringResource(Res.string.lifelist_breadcrumb),
        color = AccentCopperLight,
        fontSize = 11.sp,
        letterSpacing = 0.32.em,
        fontWeight = FontWeight.W600,
    )
}

@Composable
private fun LifelistHeadline(name: String) {
    val style =
        TextStyle(
            fontFamily = FontFamily.Serif,
            fontSize = 32.sp,
            fontWeight = FontWeight.W700,
            color = OffwhiteWarm,
        )
    if (name.isEmpty()) {
        Text(text = stringResource(Res.string.lifelist_headline_fallback), style = style)
    } else {
        ItalicMixedText(
            text = stringResource(Res.string.lifelist_headline_format, name),
            style = style,
            italicAccent = AccentCopperLight,
        )
    }
}

private data class StatItem(
    val label: String,
    val value: String,
)

@Composable
private fun StatRow(
    stats: List<StatItem>,
    onStat3Click: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        stats.forEachIndexed { index, stat ->
            if (index > 0) {
                StatDivider()
            }
            val isClickable = index == 2
            StatColumn(
                stat = stat,
                modifier = Modifier.weight(1f),
                onClick = if (isClickable) onStat3Click else null,
            )
        }
    }
}

@Composable
private fun StatColumn(
    stat: StatItem,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier =
            modifier
                .let { if (onClick != null) it.clickable(onClick = onClick) else it }
                .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stat.value,
            color = AccentCopperLight,
            fontFamily = FontFamily.Serif,
            fontStyle = FontStyle.Italic,
            fontSize = 24.sp,
            fontWeight = FontWeight.W700,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = stat.label,
            color = OffwhiteWarm.copy(alpha = 0.8f),
            fontSize = 9.sp,
            fontWeight = FontWeight.W600,
            letterSpacing = 0.18.em,
            maxLines = 1,
        )
    }
}

@Composable
private fun StatDivider() {
    Box(
        modifier =
            Modifier
                .width(1.dp)
                .height(36.dp)
                .background(AccentCopper.copy(alpha = 0.25f)),
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
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(50))
                .background(SandCreme)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = AccentCopper, fontSize = 12.sp, fontWeight = FontWeight.W600)
    }
}

// ─── Stamp row ────────────────────────────────────────────────────────────────

@Composable
private fun LifelistRowComposable(
    row: LifelistRow,
    now: Instant,
    onClick: () -> Unit,
) {
    val justStamped = (now.toEpochMilliseconds() - row.observation.savedAt.toEpochMilliseconds()) < 24L * 3_600_000L
    val bg = if (justStamped) AccentCopper.copy(alpha = 0.08f) else SandCreme

    val confidencePct = (row.observation.confidence * 100f).toInt()
    val matchColor = matchColor(confidencePct)

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(bg)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Circular thumb + StampNumberBadge overlay
        Box(modifier = Modifier.size(50.dp)) {
            CircularThumb(
                photoPath = row.observation.photoPath,
                modifier = Modifier.matchParentSize(),
            )
            StampNumberBadge(
                number = row.observation.stampNumber,
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.species?.name ?: row.observation.speciesId,
                color = TextOnCreme,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.W700,
                fontSize = 16.sp,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${row.species?.scientificName ?: ""} · ${relativeTime(row.observation.savedAt, now)}",
                color = TextOnCreme.copy(alpha = 0.6f),
                fontSize = 12.sp,
                fontStyle = FontStyle.Italic,
            )
        }

        // Match-% label
        Text(
            text = "$confidencePct%",
            color = matchColor,
            fontWeight = FontWeight.W700,
            fontSize = 14.sp,
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

private fun matchColor(pct: Int): Color =
    when {
        pct >= 80 -> MatchHigh
        pct >= 60 -> MatchMid
        else -> MatchLow
    }
