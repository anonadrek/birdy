package se.birdy.app.ui.recap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import se.birdy.app.recap.HeroFind
import se.birdy.app.recap.WeeklyRecapBuilder
import se.birdy.content.SpeciesId
import se.birdy.content.model.SpeciesSummary
import se.birdy.domain.badge.BadgeCatalog
import se.birdy.domain.badge.BadgeRepository
import se.birdy.domain.badge.BadgeUnlock
import se.birdy.domain.observation.Observation
import se.birdy.domain.observation.ObservationRepository
import se.birdy.domain.observation.ObservationSource

/**
 * The weekly recap, "Veckans uppslag" (release 1.3.0 Task 7j, design "Uppslag 1"): the week's
 * numbers, the Monday-to-Sunday strip, new species with their life-list number, the week's stamps
 * and every find. [stampFor] looks a stamp up in the badge catalog ([recapStampResolver]); it
 * returns null for an unlock whose badge is no longer in the catalog, which the card then skips.
 */
class RecapViewModel(
    private val obsRepo: ObservationRepository,
    private val badgeRepo: BadgeRepository,
    private val speciesByQid: suspend () -> Map<SpeciesId, SpeciesSummary>,
    private val stampFor: suspend (String) -> RecapStampItem?,
    private val zone: TimeZone,
    private val now: () -> Instant = { Clock.System.now() },
) : ViewModel() {
    private val builder = WeeklyRecapBuilder(zone)

    val state: StateFlow<RecapUiState> =
        combine(obsRepo.observeAll(), badgeRepo.observeUnlocks()) { obs, unlocks ->
            // Upcast Loaded → RecapUiState so catch can emit sibling subtypes (Error).
            // This is a safe widening — not a downcast.
            buildState(obs, unlocks) as RecapUiState
        }.catch { emit(RecapUiState.Error(RecapErrorKind.LoadFailed)) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecapUiState.Loading)

    private suspend fun buildState(
        obs: List<Observation>,
        unlocks: List<BadgeUnlock>,
    ): RecapUiState {
        val species = speciesByQid()
        val recap = builder.build(obs, species, unlocks, now())
        val items = recap.finds.associate { f -> f.observationId to f.toItem(species) }
        val weekBadgeIds = recap.summary.newBadgeIds.toSet()
        val stamps =
            buildList {
                unlocks
                    .filter { it.badgeId in weekBadgeIds }
                    .sortedByDescending { it.unlockedAt }
                    .map { it.badgeId }
                    .distinct()
                    .forEach { id -> stampFor(id)?.let { add(it) } }
            }
        return RecapUiState.Loaded(
            recap = recap,
            finds = recap.finds.map { items.getValue(it.observationId) },
            days =
                recap.days.map { day ->
                    RecapDayItem(
                        date = day.date,
                        findCount = day.findCount,
                        cover = day.cover?.let { items.getValue(it.observationId) },
                        isFuture = day.isFuture,
                    )
                },
            newSpecies =
                recap.newSpecies.map { new ->
                    RecapNewSpeciesItem(
                        lifeListNumber = new.lifeListNumber,
                        find = items.getValue(new.firstFind.observationId),
                    )
                },
            stamps = stamps,
        )
    }

    private fun HeroFind.toItem(species: Map<SpeciesId, SpeciesSummary>): RecapFindItem =
        RecapFindItem(
            observationId = observationId,
            speciesName = speciesId?.let { species[SpeciesId(it)]?.name },
            photoPath = photoPath,
            heroImagePath = heroImagePath,
            isNewSpecies = isNewSpecies,
            date = capturedAt.toLocalDateTime(zone).date,
            isHeard = source == ObservationSource.Audio,
        )
}

/**
 * Looks up a stamp earned this week: its number is its place in [catalog] (the "№" on Märken),
 * its ink brass for a Premium stamp. Null for a badge id that is no longer in the catalog.
 */
fun recapStampResolver(
    catalog: BadgeCatalog,
    nameFor: suspend (String) -> String,
    descriptionFor: suspend (String) -> String,
): suspend (String) -> RecapStampItem? {
    val numbers = catalog.badges.withIndex().associate { (index, badge) -> badge.id to index + 1 }
    return { badgeId ->
        catalog.findById(badgeId)?.let { badge ->
            RecapStampItem(
                badgeId = badgeId,
                stampNumber = numbers.getValue(badgeId),
                name = nameFor(badgeId),
                description = descriptionFor(badgeId),
                isPremium = badge.isPremium,
            )
        }
    }
}
