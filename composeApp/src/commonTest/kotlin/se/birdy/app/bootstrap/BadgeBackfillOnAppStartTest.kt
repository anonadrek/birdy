package se.birdy.app.bootstrap

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import se.birdy.app.badges.RecalculateBadgesUseCase
import se.birdy.app.testing.FakeBadgeRepository
import se.birdy.app.testing.FakeClock
import se.birdy.app.testing.FakeObservationRepository
import se.birdy.content.SpeciesId
import se.birdy.content.model.Species
import se.birdy.domain.badge.Badge
import se.birdy.domain.badge.BadgeCatalog
import se.birdy.domain.badge.BadgeCategory
import se.birdy.domain.badge.BadgeRule
import se.birdy.domain.badge.BadgeUnlock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BadgeBackfillOnAppStartTest {
    @Test
    fun `no-op when versionStore lastSeen equals catalog version`() =
        runTest {
            val versionStore = FakeBadgeVersionStore(initial = 1)
            val obsRepo = FakeObservationRepository()
            val badgeRepo = FakeBadgeRepository()
            val backfill = makeBackfill(catalogVersion = 1, versionStore, obsRepo, badgeRepo)

            backfill.runIfNeeded()

            assertEquals(1, versionStore.lastSeen)
            assertTrue(badgeRepo.observeUnlocks().first().isEmpty())
        }

    @Test
    fun `runs recalc when versionStore is behind catalog`() =
        runTest {
            val versionStore = FakeBadgeVersionStore(initial = 0)
            val obsRepo =
                FakeObservationRepository().apply {
                    seedObservation(speciesId = "Q25612", capturedAt = Instant.fromEpochMilliseconds(1L))
                }
            val badgeRepo = FakeBadgeRepository()
            val backfill = makeBackfill(catalogVersion = 1, versionStore, obsRepo, badgeRepo)

            backfill.runIfNeeded()

            assertEquals(1, versionStore.lastSeen)
            val unlocks = badgeRepo.observeUnlocks().first()
            assertEquals(1, unlocks.size)
            assertEquals("first_obs", unlocks[0].badgeId)
        }

    @Test
    fun `empty observations result in no-op (but updates version)`() =
        runTest {
            val versionStore = FakeBadgeVersionStore(initial = 0)
            val backfill =
                makeBackfill(
                    catalogVersion = 1,
                    versionStore = versionStore,
                    obsRepo = FakeObservationRepository(),
                    badgeRepo = FakeBadgeRepository(),
                )

            backfill.runIfNeeded()

            assertEquals(1, versionStore.lastSeen)
        }

    @Test
    fun `existing unlocks are not duplicated`() =
        runTest {
            val versionStore = FakeBadgeVersionStore(initial = 0)
            val obsRepo =
                FakeObservationRepository().apply {
                    seedObservation("Q1", Instant.fromEpochMilliseconds(1L))
                }
            val badgeRepo =
                FakeBadgeRepository().apply {
                    seedUnlocks(listOf(BadgeUnlock("first_obs", Instant.fromEpochMilliseconds(0L))))
                }
            val backfill = makeBackfill(catalogVersion = 1, versionStore, obsRepo, badgeRepo)

            backfill.runIfNeeded()

            assertEquals(1, badgeRepo.observeUnlocks().first().size)
        }

    private fun makeBackfill(
        catalogVersion: Int,
        versionStore: BadgeVersionStore,
        obsRepo: FakeObservationRepository,
        badgeRepo: FakeBadgeRepository,
    ): BadgeBackfillOnAppStart {
        val catalog =
            BadgeCatalog(
                version = catalogVersion,
                badges =
                    listOf(
                        Badge("first_obs", BadgeCategory.PROGRESSION, BadgeRule.CountUniqueSpecies(1)),
                    ),
            )
        val recalc =
            RecalculateBadgesUseCase(
                zone = TimeZone.UTC,
                clock = FakeClock(now = Instant.fromEpochMilliseconds(1_800_000_000_000)),
            )
        val species: suspend () -> Map<SpeciesId, Species> = { emptyMap() }
        return BadgeBackfillOnAppStart(
            recalc = recalc,
            obsRepo = obsRepo,
            speciesByQid = species,
            badgeRepo = badgeRepo,
            catalog = catalog,
            versionStore = versionStore,
        )
    }
}

private class FakeBadgeVersionStore(
    initial: Int = 0,
) : BadgeVersionStore {
    override var lastSeen: Int = initial
}
