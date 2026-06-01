package se.birdy.app.ui.badges

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import se.birdy.app.testing.FakeBadgeRepository
import se.birdy.app.testing.FakeObservationRepository
import se.birdy.content.Locale
import se.birdy.content.SpeciesId
import se.birdy.content.model.Species
import se.birdy.content.model.SpeciesTaxonomy
import se.birdy.domain.badge.Badge
import se.birdy.domain.badge.BadgeCatalog
import se.birdy.domain.badge.BadgeCategory
import se.birdy.domain.badge.BadgeRule
import se.birdy.domain.badge.BadgeUnlock
import se.birdy.domain.observation.Observation
import se.birdy.domain.observation.ObservationRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BadgesViewModelTest {
    @BeforeTest
    fun setMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun resetMain() = Dispatchers.resetMain()

    @Test
    fun `empty state — 0 unlocks 0 obs`() =
        runTest {
            val vm = makeVm(observations = emptyList(), unlocks = emptyList(), totalSpecies = 700)
            vm.state.test {
                // Skip Loading
                var item = awaitItem()
                while (item is BadgesUiState.Loading) item = awaitItem()
                val loaded = item as BadgesUiState.Loaded
                assertEquals(SpeciesProgress(seen = 0, total = 700), loaded.speciesProgress)
                assertEquals(0, loaded.unlockedCount)
                assertEquals(emptyList(), loaded.recentlyUnlocked)
                assertNull(loaded.weeklyStreak)
                assertNull(loaded.monthlyStreak)
                assertTrue(loaded.locked.isNotEmpty())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `recentlyUnlocked sorted DESC and capped to 5`() =
        runTest {
            val unlocks =
                (1..7).map { i ->
                    BadgeUnlock("u$i", Instant.fromEpochMilliseconds(1_000L + i))
                }
            val catalog =
                BadgeCatalog(
                    version = 1,
                    badges = unlocks.map { Badge(it.badgeId, BadgeCategory.PROGRESSION, BadgeRule.CountUniqueSpecies(1)) },
                )
            val vm = makeVm(observations = emptyList(), unlocks = unlocks, totalSpecies = 700, catalog = catalog)

            vm.state.test {
                var item = awaitItem()
                while (item is BadgesUiState.Loading) item = awaitItem()
                val loaded = item as BadgesUiState.Loaded
                assertEquals(5, loaded.recentlyUnlocked.size)
                assertEquals("u7", loaded.recentlyUnlocked[0].badge.id)
                assertEquals("u3", loaded.recentlyUnlocked[4].badge.id)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `weekly streak hidden below 2`() =
        runTest {
            val obs = listOf(observation("Q1", 2026, 5, 7))
            val vm = makeVm(observations = obs, unlocks = emptyList(), totalSpecies = 700)
            vm.state.test {
                var item = awaitItem()
                while (item is BadgesUiState.Loading) item = awaitItem()
                val loaded = item as BadgesUiState.Loaded
                assertNull(loaded.weeklyStreak)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `weekly streak visible at 2 (implicit opt-in)`() =
        runTest {
            val obs =
                listOf(
                    observation("Q1", 2026, 5, 4), // v19
                    observation("Q1", 2026, 5, 11), // v20
                )
            val vm = makeVm(observations = obs, unlocks = emptyList(), totalSpecies = 700)
            vm.state.test {
                var item = awaitItem()
                while (item is BadgesUiState.Loading) item = awaitItem()
                val loaded = item as BadgesUiState.Loaded
                assertEquals(2, loaded.weeklyStreak)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `error state when observe flow throws`() =
        runTest {
            val obsRepo =
                object : ObservationRepository {
                    override fun observeAll(): Flow<List<Observation>> = flow { throw RuntimeException("boom") }

                    override fun observeAllByStampNumber(): Flow<List<Observation>> = flowOf(emptyList())

                    override fun observeById(id: String): Flow<Observation?> = flowOf(null)

                    override suspend fun insert(observation: Observation) {}

                    override suspend fun updateNote(
                        id: String,
                        note: String,
                    ) {}

                    override suspend fun delete(id: String): se.birdy.domain.observation.FileCleanupRequest =
                        se.birdy.domain.observation
                            .FileCleanupRequest(null, null)

                    override suspend fun nextStampNumber(): Int = 1

                    override suspend fun countByQid(speciesId: String): Int = 0

                    override suspend fun firstByQid(speciesId: String): Instant? = null
                }
            val badgeRepo = FakeBadgeRepository()
            val recalc =
                se.birdy.app.badges
                    .RecalculateBadgesUseCase(zone = TimeZone.UTC)
            val vm =
                BadgesViewModel(
                    obsRepo = obsRepo,
                    badgeRepo = badgeRepo,
                    speciesByQid = { emptyMap() },
                    speciesTotalCount = flowOf(700),
                    catalog = BadgeCatalog(version = 1, badges = emptyList()),
                    recalc = recalc,
                    zone = TimeZone.UTC,
                    locale = Locale.SV,
                )
            vm.state.test {
                // Skip past Loading
                var item = awaitItem()
                while (item is BadgesUiState.Loading) item = awaitItem()
                assertTrue(item is BadgesUiState.Error, "expected Error, got $item")
                assertEquals(BadgeErrorKind.LoadFailed, (item as BadgesUiState.Error).kind)
                cancelAndIgnoreRemainingEvents()
            }
        }

    private fun observation(
        speciesId: String,
        year: Int,
        month: Int,
        day: Int,
    ): Observation {
        val capturedAt = LocalDateTime(year, month, day, 12, 0).toInstant(TimeZone.UTC)
        return Observation(
            id = "obs-$speciesId-$year$month$day",
            speciesId = speciesId,
            capturedAt = capturedAt,
            savedAt = capturedAt,
            photoPath = "/tmp/$speciesId.jpg",
            note = "",
            confidence = 0.9f,
            latitude = null,
            longitude = null,
            locationLabel = null,
        )
    }

    @Test
    fun `redlisted badges show progress instead of hidden`() =
        runTest {
            val redlistedBadge = Badge("redlisted_1", BadgeCategory.REDLISTED, BadgeRule.ObservedRedListed(5))
            val catalog = BadgeCatalog(version = 1, badges = listOf(redlistedBadge))
            // One observation of a VU species — so current=1, target=5 → InProgress, not Hidden
            val vuSpeciesId = "Q99001"
            val vuSpecies =
                Species(
                    id = SpeciesId(vuSpeciesId),
                    scientificName = "Acanthis cabaret",
                    taxonomy = SpeciesTaxonomy(family = "Fringillidae", familySv = null, genus = "Acanthis", iocOrder = "Passeriformes"),
                    name = "Lesser Redpoll",
                    abundance = se.birdy.content.Abundance.OVANLIG,
                    iucnStatus = "VU",
                    regions = emptyList(),
                    season = emptyMap(),
                    description = null,
                    migration = null,
                    images = emptyList(),
                )
            val obs = listOf(observation(vuSpeciesId, 2026, 5, 10))
            val vm =
                makeVmWithSpecies(
                    observations = obs,
                    unlocks = emptyList(),
                    totalSpecies = 700,
                    catalog = catalog,
                    speciesMap = mapOf(SpeciesId(vuSpeciesId) to vuSpecies),
                )

            vm.state.test {
                var item = awaitItem()
                while (item is BadgesUiState.Loading) item = awaitItem()
                val loaded = item as BadgesUiState.Loaded
                val entry = loaded.locked.firstOrNull { it.badge.id == "redlisted_1" }
                assertTrue(entry != null, "redlisted_1 must appear in locked list")
                assertFalse(
                    entry!!.state == BadgeGridState.Hidden,
                    "redlisted badge state must not be Hidden, was ${entry.state}",
                )
                // target=5, 1 observed → InProgress(1, 5)
                assertEquals(BadgeGridState.InProgress(current = 1, target = 5), entry.state)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `unlockedCount ignores unlocks not in catalog`() =
        runTest {
            val realBadge = Badge("novice", BadgeCategory.PROGRESSION, BadgeRule.CountUniqueSpecies(5))
            val catalog = BadgeCatalog(version = 1, badges = listOf(realBadge))
            val unlocks =
                listOf(
                    BadgeUnlock("novice", Instant.fromEpochMilliseconds(1_000L)), // in catalog
                    BadgeUnlock("rare_first", Instant.fromEpochMilliseconds(2_000L)), // NOT in catalog (ghost)
                    BadgeUnlock("ghost_badge", Instant.fromEpochMilliseconds(3_000L)), // NOT in catalog (ghost)
                )
            val vm = makeVm(observations = emptyList(), unlocks = unlocks, totalSpecies = 700, catalog = catalog)

            vm.state.test {
                var item = awaitItem()
                while (item is BadgesUiState.Loading) item = awaitItem()
                val loaded = item as BadgesUiState.Loaded
                // Only "novice" is in the catalog → count must be 1, not 3
                assertEquals(1, loaded.unlockedCount)
                cancelAndIgnoreRemainingEvents()
            }
        }

    private fun makeVm(
        observations: List<Observation>,
        unlocks: List<BadgeUnlock>,
        totalSpecies: Int,
        catalog: BadgeCatalog =
            BadgeCatalog(
                version = 1,
                badges =
                    listOf(
                        Badge("novice", BadgeCategory.PROGRESSION, BadgeRule.CountUniqueSpecies(5)),
                        Badge("birder_bronze", BadgeCategory.PROGRESSION, BadgeRule.CountUniqueSpecies(25)),
                    ),
            ),
    ): BadgesViewModel {
        val obsRepo = FakeObservationRepository().apply { observations.forEach(::seedDirect) }
        val badgeRepo = FakeBadgeRepository().apply { seedUnlocks(unlocks) }
        val recalc =
            se.birdy.app.badges
                .RecalculateBadgesUseCase(zone = TimeZone.UTC)
        return BadgesViewModel(
            obsRepo = obsRepo,
            badgeRepo = badgeRepo,
            speciesByQid = { emptyMap() },
            speciesTotalCount = flowOf(totalSpecies),
            catalog = catalog,
            recalc = recalc,
            zone = TimeZone.UTC,
            locale = Locale.SV,
        )
    }

    private fun makeVmWithSpecies(
        observations: List<Observation>,
        unlocks: List<BadgeUnlock>,
        totalSpecies: Int,
        catalog: BadgeCatalog,
        speciesMap: Map<SpeciesId, Species>,
    ): BadgesViewModel {
        val obsRepo = FakeObservationRepository().apply { observations.forEach(::seedDirect) }
        val badgeRepo = FakeBadgeRepository().apply { seedUnlocks(unlocks) }
        val recalc =
            se.birdy.app.badges
                .RecalculateBadgesUseCase(zone = TimeZone.UTC)
        return BadgesViewModel(
            obsRepo = obsRepo,
            badgeRepo = badgeRepo,
            speciesByQid = { speciesMap },
            speciesTotalCount = flowOf(totalSpecies),
            catalog = catalog,
            recalc = recalc,
            zone = TimeZone.UTC,
            locale = Locale.SV,
        )
    }
}
