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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.badges_load_error
import birdy_bird_scanner.composeapp.generated.resources.badges_load_error_retry
import birdy_bird_scanner.composeapp.generated.resources.badges_locked_tooltip
import birdy_bird_scanner.composeapp.generated.resources.badges_section_recently_unlocked
import birdy_bird_scanner.composeapp.generated.resources.badges_section_to_discover
import birdy_bird_scanner.composeapp.generated.resources.badges_title
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.theme.HeroMossLight
import se.birdy.app.ui.theme.LabelOnCreme
import se.birdy.app.ui.theme.TextOnHero
import se.birdy.content.Locale
import se.birdy.domain.badge.Badge
import se.birdy.domain.badge.BadgeProgress
import se.birdy.domain.badge.BadgeUnlock

@OptIn(ExperimentalMaterial3Api::class)
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
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(Res.string.badges_title),
                        fontFamily = FontFamily.Serif,
                        fontSize = 22.sp,
                    )
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = HeroMossLight,
                        titleContentColor = TextOnHero,
                    ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when (state) {
            is BadgesUiState.Loading ->
                Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                    CircularProgressIndicator()
                }

            is BadgesUiState.Error ->
                ErrorState(
                    onRetry = onRetry,
                    modifier = Modifier.fillMaxSize().padding(padding),
                )

            is BadgesUiState.Loaded ->
                LoadedContent(
                    state = state,
                    locale = locale,
                    zone = zone,
                    now = now,
                    lockedTooltip = lockedTooltip,
                    contentPadding = padding,
                    onUnlockedClick = { badge, unlock -> onBadgeClick(badge, unlock) },
                    onLockedClick = {
                        scope.launch {
                            snackbarHostState.showSnackbar(message = lockedTooltip)
                        }
                    },
                )
        }
    }
}

@Composable
private fun LoadedContent(
    state: BadgesUiState.Loaded,
    locale: Locale,
    zone: TimeZone,
    now: Instant,
    lockedTooltip: String,
    contentPadding: PaddingValues,
    onUnlockedClick: (Badge, BadgeUnlock) -> Unit,
    onLockedClick: () -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(5),
        modifier = Modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding() + 16.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            BadgeStatHero(
                seenSpecies = state.speciesProgress.seen,
                totalSpecies = state.speciesProgress.total,
                unlockedCount = state.unlockedCount,
                totalBadges = state.totalBadges,
                weeklyStreak = state.weeklyStreak,
                monthlyStreak = state.monthlyStreak,
            )
        }

        if (state.recentlyUnlocked.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.padding(top = 12.dp)) {
                    SectionLabel(stringResource(Res.string.badges_section_recently_unlocked))
                    Spacer(Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(
                            items = state.recentlyUnlocked,
                            key = { it.badge.id },
                        ) { r ->
                            val nameRes = BadgeStringMap.nameFor(r.badge.id)
                            BadgeRecentCard(
                                localizedName = stringResource(nameRes),
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
            Column(Modifier.padding(top = 14.dp)) {
                SectionLabel(stringResource(Res.string.badges_section_to_discover, state.locked.size))
                Spacer(Modifier.height(6.dp))
            }
        }

        items(
            items = state.locked,
            key = { it.badge.id },
        ) { lbp ->
            val current = (lbp.state as? BadgeGridState.InProgress)?.current ?: 0
            BadgeCard(
                progress = BadgeProgress(badge = lbp.badge, current = current, target = lbp.badge.rule.target, unlock = null),
                contentDescription = lockedTooltip,
                onClick = onLockedClick,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = LabelOnCreme,
        fontSize = 9.sp,
        modifier = Modifier.padding(horizontal = 4.dp),
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
        TextButton(onClick = onRetry) {
            Text(stringResource(Res.string.badges_load_error_retry))
        }
    }
}
