package se.birdy.app.ui.badges

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.badges_load_error
import birdy_bird_scanner.composeapp.generated.resources.badges_load_error_retry
import birdy_bird_scanner.composeapp.generated.resources.trophy_close_remaining
import birdy_bird_scanner.composeapp.generated.resources.trophy_room_back
import birdy_bird_scanner.composeapp.generated.resources.trophy_room_headline
import birdy_bird_scanner.composeapp.generated.resources.trophy_room_intro_eyebrow
import birdy_bird_scanner.composeapp.generated.resources.trophy_room_sub
import birdy_bird_scanner.composeapp.generated.resources.trophy_room_title
import birdy_bird_scanner.composeapp.generated.resources.trophy_section_close
import birdy_bird_scanner.composeapp.generated.resources.trophy_section_rare
import birdy_bird_scanner.composeapp.generated.resources.trophy_section_recent
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.components.BackButton
import se.birdy.app.ui.components.JournalIntro
import se.birdy.app.ui.components.JournalLoading
import se.birdy.app.ui.components.JournalScaffold
import se.birdy.app.ui.components.StampSealState
import se.birdy.app.ui.theme.StampNavy
import se.birdy.app.ui.theme.TextOnCreme
import se.birdy.app.ui.theme.rememberDmSerifDisplay
import se.birdy.content.Locale

@Composable
fun TrophyRoomScreen(
    state: BadgesUiState,
    locale: Locale,
    zone: TimeZone,
    onHeroClick: (BadgeWithUnlock) -> Unit,
    onStampClick: (BadgeWithUnlock) -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    JournalScaffold(
        modifier = modifier,
        topBar = { TrophyTopBar(onBack = onBack) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                is BadgesUiState.Loading -> JournalLoading()
                is BadgesUiState.Error ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(Modifier.height(48.dp))
                        Text(stringResource(Res.string.badges_load_error))
                        TextButton(onClick = onRetry) { Text(stringResource(Res.string.badges_load_error_retry)) }
                    }
                is BadgesUiState.Loaded ->
                    LoadedTrophyRoom(
                        state = state,
                        locale = locale,
                        zone = zone,
                        onHeroClick = onHeroClick,
                        onStampClick = onStampClick,
                    )
            }
        }
    }
}

@Composable
private fun TrophyTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BackButton(onClick = onBack, contentDescription = stringResource(Res.string.trophy_room_back))
        Spacer(Modifier.size(8.dp))
        Text(
            text = stringResource(Res.string.trophy_room_title),
            fontFamily = rememberDmSerifDisplay(),
            fontStyle = FontStyle.Italic,
            fontSize = 20.sp,
            color = TextOnCreme,
        )
    }
}

@Composable
private fun LoadedTrophyRoom(
    state: BadgesUiState.Loaded,
    locale: Locale,
    zone: TimeZone,
    onHeroClick: (BadgeWithUnlock) -> Unit,
    onStampClick: (BadgeWithUnlock) -> Unit,
) {
    val showcase = state.trophyShowcase
    val waiting = (state.totalBadges - state.unlockedCount).coerceAtLeast(0)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 40.dp),
    ) {
        item {
            JournalIntro(
                label = stringResource(Res.string.trophy_room_intro_eyebrow),
                headline = stringResource(Res.string.trophy_room_headline),
                sub =
                    stringResource(
                        Res.string.trophy_room_sub,
                        state.unlockedCount.toString(),
                        state.totalBadges.toString(),
                        waiting.toString(),
                    ),
            )
        }
        item { TrophyHero(hero = showcase.hero, locale = locale, zone = zone, onHeroClick = onHeroClick) }

        if (showcase.recentlyUnlocked.isNotEmpty()) {
            item {
                TrophyBand(label = stringResource(Res.string.trophy_section_recent)) {
                    items(items = showcase.recentlyUnlocked, key = { it.badge.id }) { bwu ->
                        val name = stringResource(BadgeStringMap.nameFor(bwu.badge.id))
                        TrophyStampItem(
                            state = StampSealState.Unlocked(number = bwu.stampNumber, glyph = null, name = name),
                            onClick = { onStampClick(bwu) },
                        )
                    }
                }
            }
        }
        if (showcase.rareFinds.isNotEmpty()) {
            item {
                TrophyBand(label = stringResource(Res.string.trophy_section_rare)) {
                    items(items = showcase.rareFinds, key = { it.badge.id }) { bwu ->
                        val name = stringResource(BadgeStringMap.nameFor(bwu.badge.id))
                        TrophyStampItem(
                            state = StampSealState.Unlocked(number = bwu.stampNumber, glyph = null, name = name),
                            accentColor = StampNavy,
                            onClick = { onStampClick(bwu) },
                        )
                    }
                }
            }
        }
        if (showcase.closeToUnlock.isNotEmpty()) {
            item {
                TrophyBand(label = stringResource(Res.string.trophy_section_close)) {
                    items(items = showcase.closeToUnlock, key = { it.badge.id }) { lbp ->
                        val name = stringResource(BadgeStringMap.nameFor(lbp.badge.id))
                        val s = lbp.state as BadgeGridState.InProgress
                        TrophyStampItem(
                            state =
                                StampSealState.InProgress(
                                    number = lbp.stampNumber,
                                    name = name,
                                    progressLabel = "${s.current}/${s.target}",
                                ),
                            caption = stringResource(Res.string.trophy_close_remaining, (s.target - s.current).toString()),
                        )
                    }
                }
            }
        }
    }
}
