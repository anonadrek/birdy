package se.birdy.app.ui.badges

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.badges_journal_headline
import birdy_bird_scanner.composeapp.generated.resources.badges_journal_label
import birdy_bird_scanner.composeapp.generated.resources.badges_journal_sub
import birdy_bird_scanner.composeapp.generated.resources.badges_load_error
import birdy_bird_scanner.composeapp.generated.resources.badges_load_error_retry
import birdy_bird_scanner.composeapp.generated.resources.badges_section_habits
import birdy_bird_scanner.composeapp.generated.resources.badges_section_milestones
import birdy_bird_scanner.composeapp.generated.resources.badges_section_recently_unlocked
import birdy_bird_scanner.composeapp.generated.resources.gear_content_description
import birdy_bird_scanner.composeapp.generated.resources.premium_badges_cta
import birdy_bird_scanner.composeapp.generated.resources.premium_badges_section
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.components.GearButton
import se.birdy.app.ui.components.JournalIntro
import se.birdy.app.ui.components.JournalLoading
import se.birdy.app.ui.components.JournalScaffold
import se.birdy.app.ui.components.StampSeal
import se.birdy.app.ui.components.StampSealState
import se.birdy.app.ui.components.StampTrack
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.MarginaliaInk
import se.birdy.app.ui.theme.rememberCaveat
import se.birdy.content.Locale
import se.birdy.domain.badge.Badge
import se.birdy.domain.badge.BadgeTier
import se.birdy.domain.badge.BadgeUnlock

@Composable
fun BadgesScreen(
    state: BadgesUiState,
    locale: Locale,
    zone: TimeZone,
    onBadgeClick: (Badge, BadgeUnlock?) -> Unit,
    onRetry: () -> Unit,
    onSettingsClick: () -> Unit,
    onPremiumClick: () -> Unit,
    onOpenTrophyRoom: () -> Unit,
    showPremiumTeaser: Boolean = true,
    modifier: Modifier = Modifier,
) {
    var selectedLocked: LockedBadgeProgress? by remember { mutableStateOf(null) }
    val now = remember { Clock.System.now() }

    JournalScaffold(modifier = modifier) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                is BadgesUiState.Loading -> JournalLoading()
                is BadgesUiState.Error -> ErrorState(onRetry = onRetry, modifier = Modifier.fillMaxSize())
                is BadgesUiState.Loaded ->
                    LoadedContent(
                        state = state,
                        locale = locale,
                        zone = zone,
                        now = now,
                        onUnlockedClick = { badge, unlock -> onBadgeClick(badge, unlock) },
                        onLockedClick = { selectedLocked = it },
                        onSettingsClick = onSettingsClick,
                        onPremiumClick = onPremiumClick,
                        onOpenTrophyRoom = onOpenTrophyRoom,
                        showPremiumTeaser = showPremiumTeaser,
                    )
            }
        }
    }
    selectedLocked?.let { progress ->
        LockedBadgeBottomSheet(progress = progress, onDismiss = { selectedLocked = null })
    }
}

@Composable
private fun LoadedContent(
    state: BadgesUiState.Loaded,
    locale: Locale,
    zone: TimeZone,
    now: Instant,
    onUnlockedClick: (Badge, BadgeUnlock) -> Unit,
    onLockedClick: (LockedBadgeProgress) -> Unit,
    onSettingsClick: () -> Unit,
    onPremiumClick: () -> Unit,
    onOpenTrophyRoom: () -> Unit,
    showPremiumTeaser: Boolean,
) {
    val milestones = state.locked.filter { it.badge.category.tier == BadgeTier.MILESTONE }
    val habits = state.locked.filter { it.badge.category.tier == BadgeTier.HABIT }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(end = 0.dp, top = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                GearButton(
                    onClick = onSettingsClick,
                    contentDescription = stringResource(Res.string.gear_content_description),
                )
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            TrophyRoomEntryCard(
                hero = state.trophyShowcase.hero,
                unlockedCount = state.unlockedCount,
                onClick = onOpenTrophyRoom,
            )
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                JournalIntro(
                    label = stringResource(Res.string.badges_journal_label, state.unlockedCount.toString()),
                    headline = stringResource(Res.string.badges_journal_headline, state.unlockedCount.toString()),
                    sub = stringResource(Res.string.badges_journal_sub, (state.totalBadges - state.unlockedCount).toString()),
                    horizontalPadding = 0,
                    topPadding = 0,
                )
                StampTrack(
                    filled = state.unlockedCount,
                    total = state.totalBadges,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
        }

        if (state.recentlyUnlocked.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    SectionLabel(stringResource(Res.string.badges_section_recently_unlocked))
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(items = state.recentlyUnlocked, key = { it.badge.id }) { r ->
                            val nameRes = BadgeStringMap.nameFor(r.badge.id)
                            BadgeRecentCard(
                                localizedName = stringResource(nameRes),
                                stampNumber = r.stampNumber,
                                glyph = null,
                                unlockedAt = r.unlockedAt,
                                now = now,
                                locale = locale,
                                zone = zone,
                                onClick = { onUnlockedClick(r.badge, BadgeUnlock(r.badge.id, r.unlockedAt)) },
                            )
                        }
                    }
                }
            }
        }

        if (milestones.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionLabel(stringResource(Res.string.badges_section_milestones))
            }
            items(items = milestones, key = { it.badge.id }) { lbp ->
                BadgeGridCell(
                    progress = lbp,
                    onClick = { onLockedClick(lbp) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (habits.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionLabel(stringResource(Res.string.badges_section_habits), dim = true)
            }
            items(items = habits, key = { it.badge.id }) { lbp ->
                BadgeGridCell(
                    progress = lbp,
                    onClick = { onLockedClick(lbp) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (showPremiumTeaser || state.premiumActive) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                PremiumBadgesSection(
                    premiumActive = state.premiumActive,
                    badges = state.premiumBadges,
                    onPremiumClick = onPremiumClick,
                    onUnlockedClick = { p ->
                        p.unlock?.let { onUnlockedClick(p.badge, it) }
                    },
                    onLockedClick = { p ->
                        onLockedClick(
                            LockedBadgeProgress(
                                badge = p.badge,
                                state = p.state,
                                stampNumber = p.stampNumber,
                            ),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun PremiumBadgesSection(
    premiumActive: Boolean,
    badges: List<PremiumBadgeProgress>,
    onPremiumClick: () -> Unit,
    onUnlockedClick: (PremiumBadgeProgress) -> Unit,
    onLockedClick: (PremiumBadgeProgress) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
        Text(
            text = stringResource(Res.string.premium_badges_section),
            fontSize = 10.sp,
            fontWeight = FontWeight.W600,
            letterSpacing = 0.16.em,
            color = MarginaliaInk,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
        )
        if (premiumActive) {
            PremiumBadgesActiveGrid(
                badges = badges,
                onUnlockedClick = onUnlockedClick,
                onLockedClick = onLockedClick,
            )
        } else {
            PremiumBadgesTeaserRow(onPremiumClick = onPremiumClick)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PremiumBadgeCell(
    progress: PremiumBadgeProgress,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val nameRes = BadgeStringMap.nameFor(progress.badge.id)
    val name = stringResource(nameRes)
    val state =
        when {
            progress.unlock != null ->
                StampSealState.Unlocked(
                    number = progress.stampNumber,
                    glyph = null,
                    name = name,
                )
            progress.state is BadgeGridState.InProgress ->
                StampSealState.InProgress(
                    number = progress.stampNumber,
                    name = name,
                    progressLabel = "${progress.state.current}/${progress.state.target}",
                )
            else -> StampSealState.Locked(name = name)
        }
    StampSeal(
        state = state,
        modifier = modifier,
        onClick = onClick,
    )
}

@Composable
private fun PremiumBadgesActiveGrid(
    badges: List<PremiumBadgeProgress>,
    onUnlockedClick: (PremiumBadgeProgress) -> Unit,
    onLockedClick: (PremiumBadgeProgress) -> Unit,
) {
    val rows = badges.chunked(3)
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                row.forEach { bp ->
                    Box(modifier = Modifier.weight(1f)) {
                        PremiumBadgeCell(
                            progress = bp,
                            onClick = {
                                if (bp.unlock != null) onUnlockedClick(bp) else onLockedClick(bp)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                repeat(5 - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PremiumBadgesTeaserRow(onPremiumClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(5) {
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(CircleShape)
                        .background(Color.Transparent)
                        .border(1.5.dp, AccentCopper.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("★", color = AccentCopper.copy(alpha = 0.7f), fontSize = 16.sp)
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(999.dp))
                .background(AccentCopper)
                .clickable(onClick = onPremiumClick)
                .semantics(mergeDescendants = true) { role = Role.Button }
                .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(Res.string.premium_badges_cta),
            color = Color.White,
            fontFamily = rememberCaveat(),
            fontSize = 16.sp,
            fontWeight = FontWeight.W600,
        )
    }
}

@Composable
private fun SectionLabel(
    text: String,
    dim: Boolean = false,
) {
    Text(
        text = text.uppercase(),
        color = if (dim) MarginaliaInk.copy(alpha = 0.55f) else MarginaliaInk,
        fontSize = 9.sp,
        fontWeight = FontWeight.W700,
        letterSpacing = 0.22.em,
    )
}

@Composable
private fun ErrorState(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(48.dp))
        Text(stringResource(Res.string.badges_load_error))
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onRetry) { Text(stringResource(Res.string.badges_load_error_retry)) }
    }
}
