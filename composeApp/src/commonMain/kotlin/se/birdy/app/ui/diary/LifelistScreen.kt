package se.birdy.app.ui.diary

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
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
import birdy_bird_scanner.composeapp.generated.resources.lifelist_journal_days
import birdy_bird_scanner.composeapp.generated.resources.lifelist_journal_headline
import birdy_bird_scanner.composeapp.generated.resources.lifelist_journal_headline_anonymous
import birdy_bird_scanner.composeapp.generated.resources.lifelist_journal_headline_no_name
import birdy_bird_scanner.composeapp.generated.resources.lifelist_journal_label
import birdy_bird_scanner.composeapp.generated.resources.lifelist_journal_species_found
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
import birdy_bird_scanner.composeapp.generated.resources.onboarding_p3_fallback_name
import birdy_bird_scanner.composeapp.generated.resources.possessive_sibilant_endings
import birdy_bird_scanner.composeapp.generated.resources.possessive_suffix
import birdy_bird_scanner.composeapp.generated.resources.possessive_suffix_sibilant
import birdy_bird_scanner.composeapp.generated.resources.premium_lifelist_badge
import birdy_bird_scanner.composeapp.generated.resources.premium_lifelist_cta
import birdy_bird_scanner.composeapp.generated.resources.premium_lifelist_preview_caption
import birdy_bird_scanner.composeapp.generated.resources.premium_lifelist_title
import birdy_bird_scanner.composeapp.generated.resources.recap_eyebrow_fmt
import birdy_bird_scanner.composeapp.generated.resources.recap_lifelist_entry_title
import birdy_bird_scanner.composeapp.generated.resources.recap_summary_active_fmt
import birdy_bird_scanner.composeapp.generated.resources.sort_chip_description
import birdy_bird_scanner.composeapp.generated.resources.unknown_species_label
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.components.BirdyPill
import se.birdy.app.ui.components.BirdyPrimaryButton
import se.birdy.app.ui.components.JournalIntro
import se.birdy.app.ui.components.JournalLoading
import se.birdy.app.ui.components.JournalScaffold
import se.birdy.app.ui.components.JournalSubLine
import se.birdy.app.ui.components.LockedStatsPreview
import se.birdy.app.ui.components.MicroLabel
import se.birdy.app.ui.components.MiniStamp
import se.birdy.app.ui.components.StampSeal
import se.birdy.app.ui.components.StampSealState
import se.birdy.app.ui.components.hairlineBottom
import se.birdy.app.ui.components.parseJournalHeadline
import se.birdy.app.ui.dailybird.DailyBirdStrip
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.AccentCopperLight
import se.birdy.app.ui.theme.Hairline
import se.birdy.app.ui.theme.HeroMossLight
import se.birdy.app.ui.theme.HeroMossMid
import se.birdy.app.ui.theme.InkMuted
import se.birdy.app.ui.theme.MarginaliaInk
import se.birdy.app.ui.theme.MatchHigh
import se.birdy.app.ui.theme.MatchLow
import se.birdy.app.ui.theme.MatchMid
import se.birdy.app.ui.theme.MossCreme
import se.birdy.app.ui.theme.PhotoLoading
import se.birdy.app.ui.theme.PhotoScrim
import se.birdy.app.ui.theme.TextOnCreme
import se.birdy.app.ui.theme.TextOnHero
import se.birdy.app.ui.theme.rememberCaveat
import se.birdy.app.ui.theme.rememberDmSerifDisplay
import se.birdy.datastore.LifelistSort
import se.birdy.datastore.LifelistStat3Choice

// Pre-existing debt (LongParameterList/LongMethod/CyclomaticComplexMethod in detekt-baseline.xml): detekt keys
// baseline entries by the signature text, so Task 7d's two daily-bird parameters re-key them without
// changing the function's real size. Suppressed here instead of growing the baseline (AppScaffold precedent).
@Suppress("LongParameterList")
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
    dailyBird: se.birdy.app.dailybird.DailyBirdToday? = null,
    onDailyBirdClick: (speciesId: String) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Release 1.3.0 Task 7d (design option B): the Dagens fågel strip, under the totals.
    val dailyBirdStrip: (@Composable () -> Unit)? =
        dailyBird?.let { bird ->
            { DailyBirdStrip(bird = bird, onClick = { onDailyBirdClick(bird.speciesId) }) }
        }
    JournalScaffold { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                LifelistUiState.Loading -> JournalLoading()
                LifelistUiState.Empty -> EmptyLifelist(onScanCtaClick = onScanCtaClick, dailyBirdStrip = dailyBirdStrip)
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
                        dailyBirdStrip = dailyBirdStrip,
                    )
            }
        }
    }
}

// ─── Empty state ─────────────────────────────────────────────────────────────

@Composable
private fun EmptyLifelist(
    onScanCtaClick: () -> Unit,
    dailyBirdStrip: (@Composable () -> Unit)? = null,
) {
    val caveat = rememberCaveat()
    Column(modifier = Modifier.fillMaxSize()) {
        JournalIntro(
            label = stringResource(Res.string.lifelist_journal_label),
            headline = stringResource(Res.string.lifelist_journal_headline_anonymous),
            sub = stringResource(Res.string.lifelist_journal_sub_empty),
        )
        dailyBirdStrip?.invoke()
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
            BirdyPrimaryButton(
                text =
                    parseJournalHeadline(stringResource(Res.string.lifelist_empty_caveat_cta))
                        .joinToString("") { it.text },
                onClick = onScanCtaClick,
            )
        }
    }
}

// ─── Loaded state ─────────────────────────────────────────────────────────────

// Pre-existing debt (LongParameterList/LongMethod/CyclomaticComplexMethod in detekt-baseline.xml): detekt keys
// baseline entries by the signature text, so Task 7d's two daily-bird parameters re-key them without
// changing the function's real size. Suppressed here instead of growing the baseline (AppScaffold precedent).
@Suppress("LongParameterList", "LongMethod")
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
    dailyBirdStrip: (@Composable () -> Unit)? = null,
) {
    // Refresh every minute so relative timestamps ("just now" → "2 min ago") don't
    // freeze if the Lifelist is left open in the foreground.
    val now =
        produceState(initialValue = Clock.System.now()) {
            while (true) {
                kotlinx.coroutines.delay(60_000)
                value = Clock.System.now()
            }
        }.value
    val labelStat1 = stringResource(Res.string.lifelist_stat_species)
    val labelStat2 = stringResource(Res.string.lifelist_stat_stamps)
    val labelStat3 = labelForStat3(state.stat3.kind)
    val months = stringArrayResource(Res.array.months_short_uppercase)
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
                // displayNameOrNull (not a plain isEmpty/isBlank check): userName can hold a
                // historical onboarding fallback word ("Min"/"My", pre-1.3.0 skip-the-name-field
                // flows) — see Possessive.kt. maskedNames: "Min" always, "My" only in the current
                // UI language's own fallback word (masking "My" unconditionally would hide real
                // Swedish users named My).
                val maskedNames =
                    setOf(
                        HISTORICAL_SV_ONBOARDING_FALLBACK_NAME,
                        stringResource(Res.string.onboarding_p3_fallback_name),
                    )
                val displayName = displayNameOrNull(state.userName, maskedNames)
                JournalIntro(
                    label = stringResource(Res.string.lifelist_journal_label),
                    headline =
                        if (displayName == null) {
                            stringResource(Res.string.lifelist_journal_headline_no_name)
                        } else {
                            stringResource(
                                Res.string.lifelist_journal_headline,
                                possessive(
                                    name = displayName,
                                    suffix = stringResource(Res.string.possessive_suffix),
                                    sibilantSuffix = stringResource(Res.string.possessive_suffix_sibilant),
                                    sibilantEndings = stringResource(Res.string.possessive_sibilant_endings),
                                ),
                            )
                        },
                    sub = lifelistJournalSub(daysActive = state.daysActive, speciesCount = state.speciesCount),
                )
                StatRow(
                    stat1 = StatItem(labelStat1, state.speciesCount.toString()),
                    stat2 = StatItem(labelStat2, state.stampsCount.toString()),
                    stat3 = StatItem(labelStat3, state.stat3.value.toString()),
                    onStat3Click = onStat3Toggle,
                )
            }
        }

        if (dailyBirdStrip != null) {
            item(key = "daily-bird") {
                Box(Modifier.padding(bottom = 10.dp)) { dailyBirdStrip() }
            }
        }

        item {
            RecapEntryCard(preview = state.recapPreview, onClick = onRecapClick)
        }

        item {
            Row(
                // vertical = 2dp (was 10dp): SortChip's BirdyPill now carries its own
                // minimumInteractiveComponentSize() touch-target padding (≥48dp tall), which
                // otherwise stacks with this row's own padding and makes the row noticeably
                // taller than before.
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text =
                        pluralStringResource(Res.plurals.lifelist_section_recent, state.stampsCount, state.stampsCount)
                            .uppercase(),
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
                            pluralStringResource(
                                Res.plurals.lifelist_month_header,
                                rows.size,
                                "$monthLabel $year",
                                rows.size.toString(),
                            ),
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
                // T12c C2: matches the "SENASTE · N STÄMPLAR" section label's own style/uppercasing
                // above (see the recent-sort row) instead of its own one-off letter-spaced sentence
                // case, so the two section labels on this screen read consistently.
                SectionLabel(text = stringResource(Res.string.premium_lifelist_title))
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
                // T12c C2: see the comment on the same style match in the showPremiumTeaser
                // branch above.
                SectionLabel(text = stringResource(Res.string.premium_lifelist_title))
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

/**
 * T12d Minor C2: the premium-teaser and live-preview section labels each hardcoded their own
 * 24dp horizontal padding — 8dp wider than the "SENASTE · N STÄMPLAR" row above (16dp) and the
 * card below it (16dp), a visible jog between the two. Shared here so both call sites stay at
 * 16dp and can't drift apart again.
 */
@Composable
private fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(),
        color = MarginaliaInk,
        fontSize = 9.sp,
        fontWeight = FontWeight.W700,
        letterSpacing = 0.22.em,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

// ─── Recap entry card ─────────────────────────────────────────────────────────

// The photo overlay exists so the kicker/title/sub text and the chevron clear WCAG AA even over
// a blown-out (near-white) find photo. Review fix wave T8c, 2026-09-27; proof in
// RecapEntryCardContrastTest. Neutral and flat since 2026-10-06 (was HeroMossDeep fading
// 0.92 -> 0.85 -> 0.55 left to right): the photos keep their own colors, darkened only as far as
// the text needs, and the darkening no longer assumes where the weight(1f) text column ends
// (anywhere from ~89% to over 95% of the card width). 0.70 is the lightest 0.05 step that clears
// AA (the apricot kicker, 4.64:1 over pure white).
internal const val RECAP_OVERLAY_ALPHA = 0.70f

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
                .clip(RoundedCornerShape(18.dp))
                // Behind the find photos: neutral while they load, never a green flash. No
                // photos yet: the moss card, as designed.
                .then(
                    if (photos.isNotEmpty()) {
                        Modifier.background(PhotoLoading)
                    } else {
                        Modifier.background(Brush.verticalGradient(listOf(HeroMossLight, HeroMossMid)))
                    },
                ).clickable(onClick = onClick),
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
                        .background(PhotoScrim.copy(alpha = RECAP_OVERLAY_ALPHA)),
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
                        .background(AccentCopper)
                        .clearAndSetSemantics {},
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = preview.isoWeek.toString(),
                    color = TextOnHero,
                    fontFamily = caveat,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                MicroLabel(
                    text = stringResource(Res.string.recap_eyebrow_fmt, preview.isoWeek.toString()),
                    color = AccentCopperLight,
                )
                Text(
                    text = stringResource(Res.string.recap_lifelist_entry_title),
                    color = TextOnHero,
                    fontFamily = serif,
                    fontStyle = FontStyle.Normal,
                    fontSize = 20.sp,
                )
                if (preview.findCount > 0) {
                    Text(
                        text =
                            pluralStringResource(
                                Res.plurals.recap_summary_active_fmt,
                                preview.findCount,
                                preview.findCount,
                            ),
                        color = TextOnHero.copy(alpha = 0.8f),
                        fontFamily = caveat,
                        fontSize = 15.sp,
                    )
                }
            }
            Text(
                text = "›",
                color = TextOnHero,
                fontFamily = serif,
                fontStyle = FontStyle.Normal,
                fontSize = 22.sp,
                modifier = Modifier.clearAndSetSemantics {},
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
                .background(MossCreme)
                .hairlineBottom()
                .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        JournalSubLine(text = text, modifier = Modifier.semantics { heading() })
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
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatColumn(stat = stat1)
        StatSeparator()
        StatColumn(stat = stat2)
        StatSeparator()
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
            fontStyle = FontStyle.Normal,
            fontWeight = FontWeight.Normal,
            fontSize = 30.sp,
            // Explicit — otherwise this inherits the theme's bodyLarge 22sp line height (tighter
            // than the 30sp glyph itself), which clips the glyph/ripple bounds. Mirrors the
            // gotcha documented on MicroLabel.
            lineHeight = 34.sp,
        )
        Text(
            text = stat.label.uppercase(),
            color = InkMuted,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.W600,
            letterSpacing = 0.14.em,
        )
    }
}

@Composable
private fun StatSeparator() {
    Box(
        modifier =
            Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(Hairline),
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
    BirdyPill(
        text = label,
        onClick = onClick,
        contentDescription = stringResource(Res.string.sort_chip_description, label),
    )
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
                .hairlineBottom()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MiniStamp(number = row.observation.stampNumber, photoPath = row.observation.photoPath, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            val unknownLabel = stringResource(Res.string.unknown_species_label)
            Text(
                text = row.species?.name ?: row.observation.speciesId ?: unknownLabel,
                color = TextOnCreme,
                fontFamily = serif,
                fontStyle = FontStyle.Normal,
                fontSize = 17.sp,
            )
            Text(
                text = lifelistMetaLine(row.species?.scientificName, relativeTime(row.observation.savedAt, now)),
                color = InkMuted,
                fontSize = 12.sp,
            )
        }
        if (showsConfidence(row.observation)) {
            Text(
                text = "$confidencePct%",
                color = matchColor,
                fontFamily = caveat,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
        }
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

/**
 * "12 dagar. 30 arter funna." under the journal headline. Each count has its own plural, so one
 * day and one species read "1 dag. 1 art funnen." (release 1.3.0 Task 7g; was "1 dagar. 1 funna.").
 */
@Composable
internal fun lifelistJournalSub(
    daysActive: Int,
    speciesCount: Int,
): String =
    stringResource(
        Res.string.lifelist_journal_sub,
        pluralStringResource(Res.plurals.lifelist_journal_days, daysActive, daysActive),
        pluralStringResource(Res.plurals.lifelist_journal_species_found, speciesCount, speciesCount),
    )
