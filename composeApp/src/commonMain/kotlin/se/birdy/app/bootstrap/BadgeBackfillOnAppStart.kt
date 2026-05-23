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
        // Split read-side and write-side so a persist failure leaves lastSeen unchanged
        // (next app-start retries). Read-side failures (DB unavailable) are best-effort —
        // the same code runs every Save via RecalculateBadgesUseCase.
        val backfill =
            runCatching {
                val obs = obsRepo.observeAll().first()
                val species = speciesByQid()
                val existing =
                    badgeRepo
                        .observeUnlocks()
                        .first()
                        .map { it.badgeId }
                        .toSet()
                recalc.newUnlocks(obs, species, catalog, existing)
            }.onFailure { if (it is CancellationException) throw it }
                .getOrNull() ?: return
        runCatching {
            badgeRepo.persist(backfill)
            versionStore.lastSeen = catalog.version
        }.onFailure { if (it is CancellationException) throw it }
    }

    /**
     * Plan 6b3 T18: re-runs the badge recalc when Premium just flipped on so any
     * premium-only badges the user already qualifies for unlock immediately.
     * `newUnlocks` is idempotent against `existingUnlocks`, so this is safe to
     * call any time.
     */
    suspend fun runIfPremiumNewlyActive() {
        runCatching {
            val obs = obsRepo.observeAll().first()
            val species = speciesByQid()
            val existing =
                badgeRepo
                    .observeUnlocks()
                    .first()
                    .map { it.badgeId }
                    .toSet()
            val newUnlocks = recalc.newUnlocks(obs, species, catalog, existing)
            if (newUnlocks.isNotEmpty()) badgeRepo.persist(newUnlocks)
        }.onFailure {
            if (it is CancellationException) throw it
        }
    }
}
