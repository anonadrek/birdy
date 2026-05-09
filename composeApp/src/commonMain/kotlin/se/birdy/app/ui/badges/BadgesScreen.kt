package se.birdy.app.ui.badges

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import birdy_bird_scanner.composeapp.generated.resources.badges_locked_tooltip
import birdy_bird_scanner.composeapp.generated.resources.badges_section_recently_unlocked
import birdy_bird_scanner.composeapp.generated.resources.badges_section_to_discover
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.components.JournalIntro
import se.birdy.app.ui.components.StampTrack
import se.birdy.app.ui.theme.MarginaliaInk
import se.birdy.app.ui.theme.paperBackground
import se.birdy.content.Locale
import se.birdy.domain.badge.Badge
import se.birdy.domain.badge.BadgeUnlock

@Composable
fun BadgesScreen(
    state: BadgesUiState,
    locale: Locale,
    zone: TimeZone,
    onBadgeClick: (Badge, BadgeUnlock?) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val lockedTooltip = stringResource(Res.string.badges_locked_tooltip)
    val now = remember { Clock.System.now() }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().paperBackground().padding(padding)) {
            when (state) {
                is BadgesUiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                is BadgesUiState.Error -> ErrorState(onRetry = onRetry, modifier = Modifier.fillMaxSize())
                is BadgesUiState.Loaded ->
                    LoadedContent(
                        state = state,
                        locale = locale,
                        zone = zone,
                        now = now,
                        onUnlockedClick = { badge, unlock -> onBadgeClick(badge, unlock) },
                        onLockedClick = {
                            scope.launch { snackbarHostState.showSnackbar(message = lockedTooltip) }
                        },
                    )
            }
        }
    }
}

@Composable
private fun LoadedContent(
    state: BadgesUiState.Loaded,
    locale: Locale,
    zone: TimeZone,
    now: Instant,
    onUnlockedClick: (Badge, BadgeUnlock) -> Unit,
    onLockedClick: () -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                JournalIntro(
                    label = stringResource(Res.string.badges_journal_label, state.unlockedCount.toString()),
                    headline = stringResource(Res.string.badges_journal_headline, state.unlockedCount.toString()),
                    sub = stringResource(Res.string.badges_journal_sub, (state.totalBadges - state.unlockedCount).toString()),
                    horizontalPadding = 0,
                    topPadding = 24,
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

        item(span = { GridItemSpan(maxLineSpan) }) {
            Column { SectionLabel(stringResource(Res.string.badges_section_to_discover, state.locked.size)) }
        }

        items(items = state.locked, key = { it.badge.id }) { lbp ->
            BadgeGridCell(
                progress = lbp,
                onClick = onLockedClick,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = MarginaliaInk,
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
