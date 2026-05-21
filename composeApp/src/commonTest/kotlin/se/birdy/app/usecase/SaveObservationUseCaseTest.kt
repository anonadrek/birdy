package se.birdy.app.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import se.birdy.app.badges.RecalculateBadgesUseCase
import se.birdy.app.testing.FakeBadgeRepository
import se.birdy.app.testing.FakeClock
import se.birdy.app.testing.FakeObservationRepository
import se.birdy.app.testing.FakePhotoStorage
import se.birdy.content.Abundance
import se.birdy.content.SpeciesId
import se.birdy.content.model.Species
import se.birdy.content.model.SpeciesTaxonomy
import se.birdy.domain.badge.Badge
import se.birdy.domain.badge.BadgeCatalog
import se.birdy.domain.badge.BadgeCategory
import se.birdy.domain.badge.BadgeRepository
import se.birdy.domain.badge.BadgeRule
import se.birdy.domain.badge.BadgeSeason
import se.birdy.domain.badge.BadgeUnlock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

class SaveObservationUseCaseTest {
    private val capturedAt = Instant.parse("2026-05-06T10:00:00Z")
    private val clock = FakeClock(now = Instant.parse("2026-05-06T10:00:30Z"))
    private val sampleBytes = ByteArray(8) { 0x42 }

    @Test
    fun success_inserts_row_and_persists_photo() =
        runTest {
            val obsRepo = FakeObservationRepository()
            val storage = FakePhotoStorage()
            val useCase =
                SaveObservationUseCase(
                    repo = obsRepo,
                    badgeRepo = FakeBadgeRepository(),
                    photoStorage = storage,
                    clock = clock,
                    catalog = BadgeCatalog(version = 1, badges = emptyList()),
                    recalculate = RecalculateBadgesUseCase(zone = TimeZone.UTC, clock = clock),
                    speciesByQid = { emptyMap() },
                )
            val result =
                useCase.save(
                    speciesId = "Q25485",
                    capturedAt = capturedAt,
                    confidence = 0.87f,
                    rawJpegBytes = sampleBytes,
                    note = "vid mataren",
                )
            assertTrue(result.observationId.isNotBlank())
            val rows = obsRepo.observeAll().first()
            assertEquals(1, rows.size)
            assertEquals(result.observationId, rows[0].id)
            assertEquals("vid mataren", rows[0].note)
            assertEquals(1, storage.persisted.size)
        }

    @Test
    fun saved_at_uses_clock() =
        runTest {
            val obsRepo = FakeObservationRepository()
            val storage = FakePhotoStorage()
            val useCase =
                SaveObservationUseCase(
                    repo = obsRepo,
                    badgeRepo = FakeBadgeRepository(),
                    photoStorage = storage,
                    clock = clock,
                    catalog = BadgeCatalog(version = 1, badges = emptyList()),
                    recalculate = RecalculateBadgesUseCase(zone = TimeZone.UTC, clock = clock),
                    speciesByQid = { emptyMap() },
                )
            useCase.save("Q25485", capturedAt, 0.5f, sampleBytes, "")
            val row = obsRepo.observeAll().first().single()
            assertEquals(clock.now, row.savedAt)
            assertEquals(capturedAt, row.capturedAt)
        }

    @Test
    fun photo_fail_does_not_insert_row() =
        runTest {
            val obsRepo = FakeObservationRepository()
            val storage = FakePhotoStorage().apply { failOnPersist = RuntimeException("boom") }
            val useCase =
                SaveObservationUseCase(
                    repo = obsRepo,
                    badgeRepo = FakeBadgeRepository(),
                    photoStorage = storage,
                    clock = clock,
                    catalog = BadgeCatalog(version = 1, badges = emptyList()),
                    recalculate = RecalculateBadgesUseCase(zone = TimeZone.UTC, clock = clock),
                    speciesByQid = { emptyMap() },
                )
            assertFails {
                useCase.save("Q25485", capturedAt, 0.5f, sampleBytes, "")
            }
            assertEquals(emptyList(), obsRepo.observeAll().first())
            assertEquals(0, storage.persisted.size)
        }

    @Test
    fun db_fail_after_photo_cleans_up_photo() =
        runTest {
            val obsRepo = FakeObservationRepository().apply { failOnInsert = RuntimeException("db boom") }
            val storage = FakePhotoStorage()
            val useCase =
                SaveObservationUseCase(
                    repo = obsRepo,
                    badgeRepo = FakeBadgeRepository(),
                    photoStorage = storage,
                    clock = clock,
                    catalog = BadgeCatalog(version = 1, badges = emptyList()),
                    recalculate = RecalculateBadgesUseCase(zone = TimeZone.UTC, clock = clock),
                    speciesByQid = { emptyMap() },
                )
            assertFails {
                useCase.save("Q25485", capturedAt, 0.5f, sampleBytes, "")
            }
            assertEquals(0, storage.persisted.size)
        }

    @Test
    fun delete_failure_during_cleanup_is_swallowed() =
        runTest {
            val obsRepo = FakeObservationRepository().apply { failOnInsert = RuntimeException("db boom") }
            val storage = FakePhotoStorage().apply { deleteThrows = true }
            val useCase =
                SaveObservationUseCase(
                    repo = obsRepo,
                    badgeRepo = FakeBadgeRepository(),
                    photoStorage = storage,
                    clock = clock,
                    catalog = BadgeCatalog(version = 1, badges = emptyList()),
                    recalculate = RecalculateBadgesUseCase(zone = TimeZone.UTC, clock = clock),
                    speciesByQid = { emptyMap() },
                )
            // The DB-failure still propagates; the photo-delete-failure is silently caught.
            val ex = assertFails { useCase.save("Q25485", capturedAt, 0.5f, sampleBytes, "") }
            assertEquals("db boom", ex.message)
        }

    @Test
    fun `save returns SaveResult with empty unlocks when no rule matches`() =
        runTest {
            val obsRepo = FakeObservationRepository()
            val badgeRepo = FakeBadgeRepository()
            val photoStorage = FakePhotoStorage()
            val clock = FakeClock(Instant.fromEpochMilliseconds(1_800_000_000_000))
            val catalog =
                BadgeCatalog(
                    version = 1,
                    badges = listOf(Badge("novice", BadgeCategory.PROGRESSION, BadgeRule.CountUniqueSpecies(5))),
                )
            val recalc = RecalculateBadgesUseCase(zone = TimeZone.UTC, clock = clock)
            val useCase =
                SaveObservationUseCase(
                    repo = obsRepo,
                    badgeRepo = badgeRepo,
                    photoStorage = photoStorage,
                    clock = clock,
                    catalog = catalog,
                    recalculate = recalc,
                    speciesByQid = { emptyMap() },
                )

            val result =
                useCase.save(
                    speciesId = "Q25612",
                    capturedAt = clock.now(),
                    confidence = 0.9f,
                    rawJpegBytes = ByteArray(10),
                    note = "",
                )

            assertEquals(emptyList(), result.newUnlocks)
            assertEquals(1, obsRepo.observeAll().first().size)
        }

    @Test
    fun `save returns single unlock when first observation matches novice rule with target 1`() =
        runTest {
            val catalog =
                BadgeCatalog(
                    version = 1,
                    badges = listOf(Badge("first_obs", BadgeCategory.PROGRESSION, BadgeRule.CountUniqueSpecies(1))),
                )
            val obsRepo = FakeObservationRepository()
            val badgeRepo = FakeBadgeRepository()
            val photoStorage = FakePhotoStorage()
            val clock = FakeClock(Instant.fromEpochMilliseconds(1_800_000_000_000))
            val recalc = RecalculateBadgesUseCase(zone = TimeZone.UTC, clock = clock)
            val useCase =
                SaveObservationUseCase(obsRepo, badgeRepo, photoStorage, clock, catalog, recalc, { emptyMap() })

            val result = useCase.save("Q25612", clock.now(), 0.9f, ByteArray(10), "")

            assertEquals(1, result.newUnlocks.size)
            assertEquals("first_obs", result.newUnlocks[0].badgeId)
            assertEquals(1, badgeRepo.observeUnlocks().first().size)
        }

    @Test
    fun `save with multiple matching badges returns all unlocks in catalog order`() =
        runTest {
            val catalog =
                BadgeCatalog(
                    version = 1,
                    badges =
                        listOf(
                            Badge("first_obs", BadgeCategory.PROGRESSION, BadgeRule.CountUniqueSpecies(1)),
                            Badge("first_in_family", BadgeCategory.FAMILY, BadgeRule.ObservedInFamily("paridae", 1)),
                            Badge("first_spring", BadgeCategory.SEASON, BadgeRule.ObservedInSeason(BadgeSeason.SPRING, 1)),
                        ),
                )
            val species = mapOf(SpeciesId("Q25612") to fakeSpecies("Q25612", family = "paridae"))
            val obsRepo = FakeObservationRepository()
            val badgeRepo = FakeBadgeRepository()
            val photoStorage = FakePhotoStorage()
            val capturedAt = LocalDateTime(2026, 5, 7, 12, 0).toInstant(TimeZone.UTC)
            val clock = FakeClock(capturedAt)
            val recalc = RecalculateBadgesUseCase(zone = TimeZone.UTC, clock = clock)
            val useCase =
                SaveObservationUseCase(obsRepo, badgeRepo, photoStorage, clock, catalog, recalc, { species })

            val result = useCase.save("Q25612", capturedAt, 0.9f, ByteArray(10), "")

            assertEquals(listOf("first_obs", "first_in_family", "first_spring"), result.newUnlocks.map { it.badgeId })
        }

    @Test
    fun `recalc failure after successful insert returns SaveResult with empty unlocks`() =
        runTest {
            val obsRepo = FakeObservationRepository()
            val badgeRepo =
                object : BadgeRepository {
                    override fun observeUnlocks(): Flow<List<BadgeUnlock>> = flowOf(emptyList())

                    override suspend fun persist(unlocks: List<BadgeUnlock>) = error("simulated DB failure")

                    override suspend fun deleteAll() = Unit

                    override suspend fun unlockManualBadge(badgeId: String, unlockedAt: Instant): Boolean = false
                }
            val catalog =
                BadgeCatalog(
                    version = 1,
                    badges =
                        listOf(
                            Badge("first_obs", BadgeCategory.PROGRESSION, BadgeRule.CountUniqueSpecies(1)),
                        ),
                )
            val photoStorage = FakePhotoStorage()
            val clock = FakeClock(Instant.fromEpochMilliseconds(1L))
            val recalc = RecalculateBadgesUseCase(zone = TimeZone.UTC, clock = clock)
            val useCase =
                SaveObservationUseCase(obsRepo, badgeRepo, photoStorage, clock, catalog, recalc, { emptyMap() })

            val result = useCase.save("Q25612", clock.now(), 0.9f, ByteArray(10), "")

            assertEquals(emptyList(), result.newUnlocks)
            assertEquals(1, obsRepo.observeAll().first().size) // observation finns kvar
        }

    @Test
    fun `existing unlocks are not re-emitted`() =
        runTest {
            val catalog =
                BadgeCatalog(
                    version = 1,
                    badges = listOf(Badge("first_obs", BadgeCategory.PROGRESSION, BadgeRule.CountUniqueSpecies(1))),
                )
            val obsRepo = FakeObservationRepository()
            val badgeRepo =
                FakeBadgeRepository().apply {
                    seedUnlocks(listOf(BadgeUnlock("first_obs", Instant.fromEpochMilliseconds(1L))))
                }
            val photoStorage = FakePhotoStorage()
            val clock = FakeClock(Instant.fromEpochMilliseconds(2L))
            val recalc = RecalculateBadgesUseCase(zone = TimeZone.UTC, clock = clock)
            val useCase =
                SaveObservationUseCase(obsRepo, badgeRepo, photoStorage, clock, catalog, recalc, { emptyMap() })

            val result = useCase.save("Q25612", clock.now(), 0.9f, ByteArray(10), "")

            assertEquals(emptyList(), result.newUnlocks)
        }

    private fun fakeSpecies(
        qid: String,
        family: String = "unknown",
    ): Species =
        Species(
            id = SpeciesId(qid),
            scientificName = "Fakeus speciesius",
            taxonomy =
                SpeciesTaxonomy(
                    family = family,
                    familySv = null,
                    genus = "Fakeus",
                    iocOrder = "Fakeiformes",
                ),
            name = qid,
            abundance = Abundance.OVANLIG,
            iucnStatus = "LC",
            regions = emptyList(),
            season = emptyMap(),
            description = null,
            migration = null,
            images = emptyList(),
        )
}
