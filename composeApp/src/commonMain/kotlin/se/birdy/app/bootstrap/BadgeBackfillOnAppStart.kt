package se.birdy.app.bootstrap

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import se.birdy.app.badges.RecalculateBadgesUseCase
import se.birdy.content.SpeciesId
import se.birdy.content.model.Species
import se.birdy.domain.badge.BadgeCatalog
import se.birdy.domain.badge.BadgeRepository
import se.birdy.domain.observation.ObservationRepository

class BadgeBackfillOnAppStart(
    private val recalc: RecalculateBadgesUseCase,
    private val obsRepo: ObservationRepository,
    private val speciesByQid: suspend () -> Map<SpeciesId, Species>,
    private val badgeRepo: BadgeRepository,
    private val catalog: BadgeCatalog,
    private val versionStore: BadgeVersionStore,
) {
    suspend fun runIfNeeded() {
        if (versionStore.lastSeen >= catalog.version) return
        runCatching {
            val obs = obsRepo.observeAll().first()
            val species = speciesByQid()
            val existing =
                badgeRepo
                    .observeUnlocks()
                    .first()
                    .map { it.badgeId }
                    .toSet()
            val backfill = recalc.newUnlocks(obs, species, catalog, existing)
            badgeRepo.persist(backfill)
            versionStore.lastSeen = catalog.version
        }.onFailure {
            if (it is CancellationException) throw it
            // Log warning — on next app-start or Save the recalc will run again.
        }
    }
}
