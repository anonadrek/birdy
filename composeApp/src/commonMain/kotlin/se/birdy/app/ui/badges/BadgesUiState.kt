package se.birdy.app.ui.badges

import kotlinx.datetime.Instant
import se.birdy.domain.badge.Badge

sealed interface BadgesUiState {
    data object Loading : BadgesUiState

    data class Loaded(
        val speciesProgress: SpeciesProgress,
        val unlockedCount: Int,
        val totalBadges: Int,
        val weeklyStreak: Int?, // null = dölj pillet
        val monthlyStreak: Int?,
        val recentlyUnlocked: List<BadgeWithUnlock>, // upp till 5 senaste, DESC
        val locked: List<LockedBadgeProgress>, // alla låsta, sorterade
    ) : BadgesUiState

    data class Error(
        val kind: BadgeErrorKind,
    ) : BadgesUiState
}

data class SpeciesProgress(
    val seen: Int,
    val total: Int,
)

data class BadgeWithUnlock(
    val badge: Badge,
    val unlockedAt: Instant,
)

enum class BadgeErrorKind { CatalogParseFailed, LoadFailed }

sealed interface BadgeGridState {
    data object Locked : BadgeGridState

    data object Hidden : BadgeGridState

    data class InProgress(
        val current: Int,
        val target: Int,
    ) : BadgeGridState
}

data class LockedBadgeProgress(
    val badge: Badge,
    val state: BadgeGridState,
)
