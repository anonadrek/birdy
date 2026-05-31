package se.birdy.app.ui.scaffold

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.datetime.Instant
import se.birdy.app.di.AppGraph
import se.birdy.app.ui.badges.BadgeLadder
import se.birdy.app.ui.badges.BadgeStringMap
import se.birdy.app.ui.badges.BadgesScreen
import se.birdy.app.ui.badges.BadgesUiState
import se.birdy.app.ui.badges.UnlockBottomSheet
import se.birdy.domain.badge.Badge

@Composable
fun BadgesRoute(
    graph: AppGraph,
    onSettingsClick: () -> Unit,
    onPremiumClick: () -> Unit,
    showPremiumTeaser: Boolean = true,
) {
    val viewModel = remember(graph) { graph.badgesViewModel() }
    val state by viewModel.state.collectAsState()
    var bottomSheetUnlock by remember { mutableStateOf<Pair<Badge, Instant>?>(null) }

    BadgesScreen(
        state = state,
        locale = graph.defaultLocale,
        zone = graph.timeZone,
        onBadgeClick = { badge, unlock ->
            unlock?.let { bottomSheetUnlock = badge to it.unlockedAt }
        },
        onRetry = { /* state is a hot Flow — no manual retry needed */ },
        onSettingsClick = onSettingsClick,
        onPremiumClick = onPremiumClick,
        showPremiumTeaser = showPremiumTeaser,
    )

    bottomSheetUnlock?.let { (badge, unlockedAt) ->
        val loaded = state as? BadgesUiState.Loaded
        val stampNumber =
            loaded?.let { l ->
                l.recentlyUnlocked.firstOrNull { it.badge.id == badge.id }?.stampNumber
                    ?: l.premiumBadges.firstOrNull { it.badge.id == badge.id }?.stampNumber
            }
        val nextTier = loaded?.let { BadgeLadder.nextTier(badge, it.locked) }
        UnlockBottomSheet(
            badge = badge,
            unlockedAt = unlockedAt,
            isCelebration = false,
            locale = graph.defaultLocale,
            zone = graph.timeZone,
            nameRes = BadgeStringMap.nameFor(badge.id),
            descriptionRes = BadgeStringMap.descriptionFor(badge.id),
            onDismiss = { bottomSheetUnlock = null },
            stampNumber = stampNumber,
            nextTier = nextTier,
        )
    }
}
