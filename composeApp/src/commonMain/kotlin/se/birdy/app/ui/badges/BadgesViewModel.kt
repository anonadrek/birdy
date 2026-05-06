package se.birdy.app.ui.badges

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.TimeZone
import se.birdy.app.badges.RecalculateBadgesUseCase
import se.birdy.content.Locale
import se.birdy.content.SpeciesId
import se.birdy.content.model.Species
import se.birdy.domain.badge.BadgeCatalog
import se.birdy.domain.badge.BadgeProgress
import se.birdy.domain.badge.BadgeRepository
import se.birdy.domain.badge.BadgeUnlock
import se.birdy.domain.badge.longestMonthlyStreak
import se.birdy.domain.badge.longestWeeklyStreak
import se.birdy.domain.observation.Observation
import se.birdy.domain.observation.ObservationRepository

class BadgesViewModel(
    private val obsRepo: ObservationRepository,
    private val badgeRepo: BadgeRepository,
    private val speciesByQid: suspend () -> Map<SpeciesId, Species>,
    private val speciesTotalCount: Flow<Int>,
    private val catalog: BadgeCatalog,
    private val recalc: RecalculateBadgesUseCase,
    private val zone: TimeZone,
    @Suppress("UnusedPrivateMember")
    private val locale: Locale,
) : ViewModel() {
    val state: StateFlow<BadgesUiState> =
        combine(
            obsRepo.observeAll(),
            badgeRepo.observeUnlocks(),
            speciesTotalCount,
        ) { observations, unlocks, totalSpecies ->
            val species = speciesByQid()
            buildLoaded(observations, unlocks, totalSpecies, species) as BadgesUiState
        }.onStart { emit(BadgesUiState.Loading) }
            .catch { emit(BadgesUiState.Error(BadgeErrorKind.LoadFailed)) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                BadgesUiState.Loading,
            )

    private fun buildLoaded(
        observations: List<Observation>,
        unlocks: List<BadgeUnlock>,
        totalSpecies: Int,
        speciesMap: Map<SpeciesId, Species>,
    ): BadgesUiState.Loaded {
        val seenSpecies = observations.map { it.speciesId }.toSet().size
        val unlockedIds = unlocks.map { it.badgeId }.toSet()
        val capturedInstants = observations.map { it.capturedAt }

        val recentlyUnlocked =
            unlocks
                .sortedByDescending { it.unlockedAt }
                .take(5)
                .mapNotNull { u ->
                    catalog.findById(u.badgeId)?.let { b -> BadgeWithUnlock(b, u.unlockedAt) }
                }

        val locked =
            catalog.badges
                .filter { it.id !in unlockedIds }
                .map { b ->
                    BadgeProgress(
                        badge = b,
                        current = recalc.currentValue(b.rule, observations, speciesMap),
                        target = b.rule.target,
                        unlock = null,
                    )
                }.sortedWith(compareBy({ it.badge.category.order }, { it.badge.rule.target }))

        val weeklyStreak = longestWeeklyStreak(capturedInstants, zone).takeIf { it >= 2 }
        val monthlyStreak = longestMonthlyStreak(capturedInstants, zone).takeIf { it >= 2 }

        return BadgesUiState.Loaded(
            speciesProgress = SpeciesProgress(seen = seenSpecies, total = totalSpecies),
            unlockedCount = unlocks.size,
            totalBadges = catalog.badges.size,
            weeklyStreak = weeklyStreak,
            monthlyStreak = monthlyStreak,
            recentlyUnlocked = recentlyUnlocked,
            locked = locked,
        )
    }
}
