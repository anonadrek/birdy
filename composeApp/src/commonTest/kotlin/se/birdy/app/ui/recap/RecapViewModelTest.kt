package se.birdy.app.ui.recap

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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import se.birdy.app.testing.FakeBadgeRepository
import se.birdy.app.testing.FakeObservationRepository
import se.birdy.content.Abundance
import se.birdy.content.SpeciesId
import se.birdy.content.model.SpeciesSummary
import se.birdy.domain.badge.Badge
import se.birdy.domain.badge.BadgeCatalog
import se.birdy.domain.badge.BadgeCategory
import se.birdy.domain.badge.BadgeRule
import se.birdy.domain.badge.BadgeUnlock
import se.birdy.domain.badge.WeekKey
import se.birdy.domain.observation.FileCleanupRequest
import se.birdy.domain.observation.Observation
import se.birdy.domain.observation.ObservationRepository
import se.birdy.domain.observation.ObservationSource
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RecapViewModelTest {
    @BeforeTest fun setMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun resetMain() = Dispatchers.resetMain()

    private val fixedNow = Instant.parse("2026-05-30T12:00:00Z")

    private val talgoxe =
        SpeciesSummary(
            id = SpeciesId("Q25485"),
            name = "Talgoxe",
            scientificName = "Parus major",
            abundance = Abundance.ALLMÄN,
            heroImagePath = "Q25485/hero.webp",
        )

    private val catalog =
        BadgeCatalog(
            version = 1,
            badges =
                listOf(
                    Badge("novice", BadgeCategory.PROGRESSION, BadgeRule.CountUniqueSpecies(5)),
                    Badge("weekly_streak_4", BadgeCategory.STREAK_WEEKLY, BadgeRule.WeeklyStreak(4)),
                    Badge("premium_year", BadgeCategory.PROGRESSION, BadgeRule.CountUniqueSpecies(50), isPremium = true),
                ),
        )

    private fun vm(
        obs: FakeObservationRepository = FakeObservationRepository(),
        badges: FakeBadgeRepository = FakeBadgeRepository(),
        speciesByQid: suspend () -> Map<SpeciesId, SpeciesSummary> = { emptyMap() },
        week: WeekKey? = null,
    ) = RecapViewModel(
        obsRepo = obs,
        badgeRepo = badges,
        speciesByQid = speciesByQid,
        stampFor = recapStampResolver(catalog, nameFor = { "Name $it" }, descriptionFor = { "Desc $it" }),
        zone = TimeZone.UTC,
        now = { fixedNow },
        week = week,
    )

    private suspend fun RecapViewModel.loaded(): RecapUiState.Loaded {
        var result: RecapUiState.Loaded? = null
        state.test {
            var item = awaitItem()
            while (item is RecapUiState.Loading) item = awaitItem()
            result = item as RecapUiState.Loaded
            cancelAndIgnoreRemainingEvents()
        }
        return result!!
    }

    @Test
    fun `quiet week emits Loaded with quiet summary`() =
        runTest {
            val loaded = vm().loaded()
            assertTrue(loaded.recap.summary.isQuiet)
            assertEquals(7, loaded.days.size)
            assertTrue(loaded.finds.isEmpty() && loaded.newSpecies.isEmpty() && loaded.stamps.isEmpty())
        }

    @Test
    fun `species name resolved for this week find`() =
        runTest {
            val obsRepo = FakeObservationRepository()
            // fixedNow is 2026-05-30 (Saturday, ISO week 22 of 2026) — seed obs in the same week
            obsRepo.seedObservation(
                speciesId = "Q25485",
                capturedAt = Instant.parse("2026-05-28T10:00:00Z"), // Thursday same week
            )
            val loaded = vm(obs = obsRepo, speciesByQid = { mapOf(SpeciesId("Q25485") to talgoxe) }).loaded()
            val find = loaded.finds.single()
            assertEquals("Talgoxe", find.speciesName)
            assertEquals(LocalDate(2026, 5, 28), find.date)
            assertEquals("Q25485/hero.webp", find.heroImagePath)
            assertEquals(1, loaded.recap.summary.observationCount)
        }

    @Test
    fun `a find saved while the recap is open shows up in it`() =
        runTest {
            val obsRepo = FakeObservationRepository()
            val vm = vm(obs = obsRepo, speciesByQid = { mapOf(SpeciesId("Q25485") to talgoxe) })
            vm.state.test {
                var item = awaitItem()
                while (item !is RecapUiState.Loaded) item = awaitItem()
                assertTrue(item.finds.isEmpty())

                obsRepo.seedObservation(speciesId = "Q25485", capturedAt = Instant.parse("2026-05-30T09:00:00Z"), id = "new")
                var next = awaitItem()
                while (next !is RecapUiState.Loaded || next.finds.isEmpty()) next = awaitItem()
                assertEquals(listOf("new"), next.finds.map { it.observationId })
                assertEquals(1, next.recap.summary.observationCount)
                assertEquals(1, next.days.single { it.date == LocalDate(2026, 5, 30) }.findCount)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `the week named by the notification is built instead of the current one`() =
        runTest {
            val obsRepo = FakeObservationRepository()
            // fixedNow is in week 22; the notification named week 21 (18 to 24 May).
            obsRepo.seedObservation(speciesId = "Q25485", capturedAt = Instant.parse("2026-05-20T10:00:00Z"), id = "w21")
            obsRepo.seedObservation(speciesId = "Q25485", capturedAt = Instant.parse("2026-05-28T10:00:00Z"), id = "w22")
            val loaded = vm(obs = obsRepo, week = WeekKey(2026, 21)).loaded()
            assertEquals(WeekKey(2026, 21), loaded.recap.summary.week)
            assertEquals(listOf("w21"), loaded.finds.map { it.observationId })
            assertEquals(LocalDate(2026, 5, 18), loaded.days.first().date)

            // Without a week (Mina arter's card) it is still the current week.
            assertEquals(listOf("w22"), vm(obs = obsRepo).loaded().finds.map { it.observationId })
        }

    @Test
    fun `the strip and the new species carry the resolved finds`() =
        runTest {
            val obsRepo = FakeObservationRepository()
            obsRepo.seedObservation(speciesId = "Q25485", capturedAt = Instant.parse("2026-05-26T10:00:00Z"), id = "tue")
            val loaded = vm(obs = obsRepo, speciesByQid = { mapOf(SpeciesId("Q25485") to talgoxe) }).loaded()

            // Monday 25 May first; Tuesday has the find.
            assertEquals(LocalDate(2026, 5, 25), loaded.days.first().date)
            assertEquals(1, loaded.days[1].findCount)
            assertEquals("Talgoxe", loaded.days[1].cover?.speciesName)
            assertNull(loaded.days[0].cover)
            // Sunday 31 May is after "now" (Saturday 30 May).
            assertEquals(listOf(false, false, false, false, false, false, true), loaded.days.map { it.isFuture })

            val new = loaded.newSpecies.single()
            assertEquals(1, new.lifeListNumber)
            assertEquals("tue", new.find.observationId)
            assertEquals("Talgoxe", new.find.speciesName)
        }

    @Test
    fun `a heard find is marked so the grid shows the plate photo`() =
        runTest {
            val obsRepo = FakeObservationRepository()
            obsRepo.seedDirect(
                Observation(
                    id = "heard",
                    speciesId = "Q25485",
                    capturedAt = Instant.parse("2026-05-29T06:00:00Z"),
                    savedAt = Instant.parse("2026-05-29T06:00:00Z"),
                    photoPath = "/audio/heard.png",
                    note = "",
                    confidence = 0.8f,
                    latitude = null,
                    longitude = null,
                    locationLabel = null,
                    sourceType = ObservationSource.Audio,
                ),
            )
            val loaded = vm(obs = obsRepo, speciesByQid = { mapOf(SpeciesId("Q25485") to talgoxe) }).loaded()
            assertTrue(loaded.finds.single().isHeard)
        }

    @Test
    fun `the week's stamps get their catalog number and words and ink with the newest first`() =
        runTest {
            val badges = FakeBadgeRepository()
            badges.seedUnlocks(
                listOf(
                    BadgeUnlock("weekly_streak_4", Instant.parse("2026-05-26T10:00:00Z")),
                    BadgeUnlock("premium_year", Instant.parse("2026-05-29T10:00:00Z")),
                    BadgeUnlock("novice", Instant.parse("2026-05-01T10:00:00Z")), // an earlier week
                    BadgeUnlock("retired_badge", Instant.parse("2026-05-27T10:00:00Z")), // no longer in the catalog
                ),
            )
            val loaded = vm(badges = badges).loaded()
            assertEquals(
                listOf(
                    RecapStampItem("premium_year", 3, "Name premium_year", "Desc premium_year", isPremium = true),
                    RecapStampItem("weekly_streak_4", 2, "Name weekly_streak_4", "Desc weekly_streak_4", isPremium = false),
                ),
                loaded.stamps,
            )
        }

    @Test
    fun `stamps unlocked at the same moment put the higher number first as on Marken`() =
        runTest {
            val at = Instant.parse("2026-05-26T10:00:00Z")
            val badges = FakeBadgeRepository()
            badges.seedUnlocks(listOf(BadgeUnlock("novice", at), BadgeUnlock("weekly_streak_4", at)))
            assertEquals(listOf(2, 1), vm(badges = badges).loaded().stamps.map { it.stampNumber })
        }

    @Test
    fun `error state when observe flow throws`() =
        runTest {
            val throwingObsRepo =
                object : ObservationRepository {
                    override fun observeAll(): Flow<List<Observation>> = flow { throw RuntimeException("boom") }

                    override fun observeAllByStampNumber(): Flow<List<Observation>> = flowOf(emptyList())

                    override fun observeById(id: String): Flow<Observation?> = flowOf(null)

                    override suspend fun insert(observation: Observation) {}

                    override suspend fun updateNote(
                        id: String,
                        note: String,
                    ) {}

                    override suspend fun delete(id: String): FileCleanupRequest = FileCleanupRequest(null, null)

                    override suspend fun nextStampNumber(): Int = 1

                    override suspend fun countByQid(speciesId: String): Int = 0

                    override suspend fun firstByQid(speciesId: String): Instant? = null
                }
            val vm =
                RecapViewModel(
                    obsRepo = throwingObsRepo,
                    badgeRepo = FakeBadgeRepository(),
                    speciesByQid = { emptyMap() },
                    stampFor = { null },
                    zone = TimeZone.UTC,
                    now = { fixedNow },
                )
            vm.state.test {
                // Skip past Loading
                var item = awaitItem()
                while (item is RecapUiState.Loading) item = awaitItem()
                assertTrue(item is RecapUiState.Error, "expected Error, got $item")
                assertEquals(RecapErrorKind.LoadFailed, (item as RecapUiState.Error).kind)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
