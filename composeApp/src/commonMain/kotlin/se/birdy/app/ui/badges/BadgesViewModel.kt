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
import se.birdy.domain.badge.Badge
import se.birdy.domain.badge.BadgeCatalog
import se.birdy.domain.badge.BadgeCategory
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
    // Locale is wired through every VM uniformly via AppGraph. Badge titles/descriptions
    // are resolved in the Composable layer via BadgeStringMap + stringResource, so the
    // field is not read here today; kept for symmetry and future locale-aware logic.
    @Suppress("UnusedPrivateMember")
    private val locale: Locale,
    private val premiumActiveFlow: Flow<Boolean> = kotlinx.coroutines.flow.flowOf(false),
) : ViewModel() {
    val state: StateFlow<BadgesUiState> =
        combine(
            obsRepo.observeAll(),
            badgeRepo.observeUnlocks(),
            speciesTotalCount,
            premiumActiveFlow,
        ) { observations, unlocks, totalSpecies, premiumActive ->
            // speciesByQid() runs per upstream emission (so per save / unlock change). The
            // backing SqlDelightSpeciesRepository does 6 SELECTs ~once per visit; cheap for ~700 rows.
            val species = speciesByQid()
            // Upcast Loaded → BadgesUiState so onStart/catch can emit sibling subtypes
            // (Loading/Error). This is a safe widening — not a downcast.
            buildLoaded(observations, unlocks, totalSpecies, species, premiumActive) as BadgesUiState
        }.onStart { emit(BadgesUiState.Loading) }
            .catch { emit(BadgesUiState.Error(BadgeErrorKind.LoadFailed)) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                BadgesUiState.Loading,
            )

    @Suppress("LongParameterList")
    private fun buildLoaded(
        observations: List<Observation>,
        unlocks: List<BadgeUnlock>,
        totalSpecies: Int,
        speciesMap: Map<SpeciesId, Species>,
        premiumActive: Boolean,
    ): BadgesUiState.Loaded {
        val seenSpecies = observations.mapNotNull { it.speciesId }.toSet().size
        val unlockedIds = unlocks.map { it.badgeId }.toSet()
        val unlocksById = unlocks.associateBy { it.badgeId }
        val capturedInstants = observations.map { it.capturedAt }
        val stampNumbersById = catalog.badges.withIndex().associate { (i, b) -> b.id to (i + 1) }

        val regularBadges = catalog.badges.filter { !it.isPremium }
        val premiumBadgeDefs = catalog.badges.filter { it.isPremium }

        val recentlyUnlocked =
            unlocks
                .sortedByDescending { it.unlockedAt }
                .take(5)
                .mapNotNull { u ->
                    catalog.findById(u.badgeId)?.let { b ->
                        BadgeWithUnlock(b, u.unlockedAt, stampNumbersById[b.id] ?: 0)
                    }
                }

        val locked =
            regularBadges
                .filter { it.id !in unlockedIds }
                .map { b ->
                    LockedBadgeProgress(
                        badge = b,
                        state = computeLockedState(b, observations, speciesMap),
                        stampNumber = stampNumbersById[b.id] ?: 0,
                    )
                }.sortedWith(compareBy({ it.badge.category.order }, { it.badge.rule.target }))

        val premiumBadges =
            premiumBadgeDefs.map { b ->
                PremiumBadgeProgress(
                    badge = b,
                    unlock = unlocksById[b.id],
                    state =
                        if (b.id in unlockedIds) {
                            BadgeGridState.Locked // ignored when unlock != null
                        } else {
                            computeLockedState(b, observations, speciesMap)
                        },
                    stampNumber = stampNumbersById[b.id] ?: 0,
                )
            }

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
            premiumBadges = premiumBadges,
            premiumActive = premiumActive,
        )
    }

    private fun computeLockedState(
        badge: Badge,
        observations: List<Observation>,
        speciesByQid: Map<SpeciesId, Species>,
    ): BadgeGridState {
        if (badge.category == BadgeCategory.RARE) return BadgeGridState.Hidden
        val current = recalc.currentValue(badge.rule, observations, speciesByQid)
        return if (current > 0) {
            BadgeGridState.InProgress(current = current, target = badge.rule.target)
        } else {
            BadgeGridState.Locked
        }
    }
}
