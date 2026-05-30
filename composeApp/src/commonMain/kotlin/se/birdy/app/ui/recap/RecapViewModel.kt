package se.birdy.app.ui.recap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import se.birdy.app.recap.WeeklyRecapBuilder
import se.birdy.content.SpeciesId
import se.birdy.content.model.SpeciesSummary
import se.birdy.domain.badge.BadgeRepository
import se.birdy.domain.badge.BadgeUnlock
import se.birdy.domain.observation.Observation
import se.birdy.domain.observation.ObservationRepository

class RecapViewModel(
    private val obsRepo: ObservationRepository,
    private val badgeRepo: BadgeRepository,
    private val speciesByQid: suspend () -> Map<SpeciesId, SpeciesSummary>,
    private val badgeNameFor: (String) -> String,
    private val zone: TimeZone,
    private val now: () -> Instant = { Clock.System.now() },
) : ViewModel() {
    private val builder = WeeklyRecapBuilder(zone)

    val state: StateFlow<RecapUiState> =
        combine(obsRepo.observeAll(), badgeRepo.observeUnlocks()) { obs, unlocks ->
            buildState(obs, unlocks)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecapUiState.Loading)

    private suspend fun buildState(
        obs: List<Observation>,
        unlocks: List<BadgeUnlock>,
    ): RecapUiState {
        val species = speciesByQid()
        val recap = builder.build(obs, species, unlocks, now())
        val heroName = recap.hero?.speciesId?.let { species[SpeciesId(it)]?.name }
        val badgeNames = recap.summary.newBadgeIds.map(badgeNameFor)
        return RecapUiState.Loaded(recap, heroName, badgeNames)
    }
}
