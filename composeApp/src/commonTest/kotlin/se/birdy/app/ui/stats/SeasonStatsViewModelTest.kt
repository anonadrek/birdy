package se.birdy.app.ui.stats

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import se.birdy.app.testing.FakeObservationRepository
import se.birdy.app.testing.FakeSpeciesRepository
import se.birdy.content.Locale
import se.birdy.content.model.SpeciesImage
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SeasonStatsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val utc = TimeZone.UTC

    @BeforeTest fun setMain() = Dispatchers.setMain(dispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `empty repo emits Empty with the year and current month after onEnter`() =
        runTest(dispatcher) {
            val vm =
                SeasonStatsViewModel(
                    observationRepo = FakeObservationRepository(),
                    speciesRepo = FakeSpeciesRepository(),
                    clock = fixedClock("2026-05-22T08:00:00Z"),
                    zone = utc,
                    locale = Locale.SV,
                )
            vm.onEnter()
            assertEquals(SeasonStatsUiState.Empty(year = 2026, currentMonth = 5), vm.state.value)
        }

    @Test
    fun `loaded state has 12 months with correct observation counts`() =
        runTest(dispatcher) {
            val repo = FakeObservationRepository()
            repo.seedObservation("Q1", Instant.parse("2026-01-10T08:00:00Z"))
            repo.seedObservation("Q1", Instant.parse("2026-01-20T08:00:00Z"))
            repo.seedObservation("Q2", Instant.parse("2026-05-12T08:00:00Z"))
            val vm =
                SeasonStatsViewModel(
                    observationRepo = repo,
                    speciesRepo = FakeSpeciesRepository(),
                    clock = fixedClock("2026-05-22T08:00:00Z"),
                    zone = utc,
                    locale = Locale.EN,
                )
            vm.onEnter()
            val state = vm.state.value as SeasonStatsUiState.Loaded
            assertEquals(12, state.monthBars.size)
            assertEquals(2, state.monthBars.first { it.label == "JAN" }.observationCount)
            assertEquals(1, state.monthBars.first { it.label == "MAY" }.observationCount)
            assertTrue(state.monthBars.first { it.month == 5 }.isCurrent)
        }

    @Test
    fun `prior year observations excluded from totals`() =
        runTest(dispatcher) {
            val repo = FakeObservationRepository()
            // 2025 observations should be filtered out
            repo.seedObservation("Q1", Instant.parse("2025-12-31T08:00:00Z"))
            repo.seedObservation("Q1", Instant.parse("2025-06-15T08:00:00Z"))
            // 2026 observation kept
            repo.seedObservation("Q2", Instant.parse("2026-03-10T08:00:00Z"))
            val vm =
                SeasonStatsViewModel(
                    observationRepo = repo,
                    speciesRepo = FakeSpeciesRepository(),
                    clock = fixedClock("2026-05-22T08:00:00Z"),
                    zone = utc,
                    locale = Locale.SV,
                )
            vm.onEnter()
            val state = vm.state.value as SeasonStatsUiState.Loaded
            assertEquals(1, state.totalObservationsThisYear)
            assertEquals(1, state.totalSpeciesThisYear)
            assertEquals(0, state.monthBars.first { it.month == 6 }.observationCount)
            assertEquals(1, state.monthBars.first { it.month == 3 }.observationCount)
        }

    @Test
    fun `season breakdown buckets winter spring summer autumn meteorologically`() =
        runTest(dispatcher) {
            val repo = FakeObservationRepository()
            repo.seedObservation("Q1", Instant.parse("2026-01-15T08:00:00Z")) // winter
            repo.seedObservation("Q1", Instant.parse("2026-04-15T08:00:00Z")) // spring
            repo.seedObservation("Q2", Instant.parse("2026-07-15T08:00:00Z")) // summer
            repo.seedObservation("Q3", Instant.parse("2026-10-15T08:00:00Z")) // autumn
            repo.seedObservation("Q3", Instant.parse("2026-12-15T08:00:00Z")) // winter
            val vm =
                SeasonStatsViewModel(
                    observationRepo = repo,
                    speciesRepo = FakeSpeciesRepository(),
                    clock = fixedClock("2026-12-22T08:00:00Z"),
                    zone = utc,
                    locale = Locale.SV,
                )
            vm.onEnter()
            val state = vm.state.value as SeasonStatsUiState.Loaded
            assertEquals(2, state.seasons.winter)
            assertEquals(1, state.seasons.spring)
            assertEquals(1, state.seasons.summer)
            assertEquals(1, state.seasons.autumn)
        }

    @Test
    fun `top species sorted by count desc with stable tie-break by qid`() =
        runTest(dispatcher) {
            val repo = FakeObservationRepository()
            // Q3 has 3, Q1 has 2, Q2 has 2 (tied, expect Q1 before Q2 by qid)
            repo.seedObservation("Q3", Instant.parse("2026-02-01T08:00:00Z"))
            repo.seedObservation("Q3", Instant.parse("2026-02-02T08:00:00Z"))
            repo.seedObservation("Q3", Instant.parse("2026-02-03T08:00:00Z"))
            repo.seedObservation("Q1", Instant.parse("2026-03-01T08:00:00Z"))
            repo.seedObservation("Q1", Instant.parse("2026-03-02T08:00:00Z"))
            repo.seedObservation("Q2", Instant.parse("2026-04-01T08:00:00Z"))
            repo.seedObservation("Q2", Instant.parse("2026-04-02T08:00:00Z"))
            val vm =
                SeasonStatsViewModel(
                    observationRepo = repo,
                    speciesRepo = FakeSpeciesRepository(),
                    clock = fixedClock("2026-05-22T08:00:00Z"),
                    zone = utc,
                    locale = Locale.SV,
                )
            vm.onEnter()
            val state = vm.state.value as SeasonStatsUiState.Loaded
            assertEquals(3, state.topSpecies.size)
            assertEquals("Q3", state.topSpecies[0].qid)
            assertEquals(3, state.topSpecies[0].count)
            assertEquals("Q1", state.topSpecies[1].qid)
            assertEquals("Q2", state.topSpecies[2].qid)
        }

    @Test
    fun `only the three most seen species are kept with their plate photo`() =
        runTest(dispatcher) {
            val repo = FakeObservationRepository()
            repo.seedObservation("Q25485", Instant.parse("2026-02-01T08:00:00Z"))
            repo.seedObservation("Q25485", Instant.parse("2026-02-02T08:00:00Z"))
            repo.seedObservation("Q25234", Instant.parse("2026-02-03T08:00:00Z"))
            repo.seedObservation("Q25404", Instant.parse("2026-02-04T08:00:00Z"))
            repo.seedObservation("Q25402", Instant.parse("2026-02-05T08:00:00Z"))
            val vm =
                SeasonStatsViewModel(
                    observationRepo = repo,
                    speciesRepo = speciesWithHeroFor("Q25485"),
                    clock = fixedClock("2026-05-22T08:00:00Z"),
                    zone = utc,
                    locale = Locale.SV,
                )
            vm.onEnter()
            val state = vm.state.value as SeasonStatsUiState.Loaded
            assertEquals(listOf("Q25485", "Q25234", "Q25402"), state.topSpecies.map { it.qid })
            assertEquals("Talgoxe", state.topSpecies[0].nameLocalized)
            assertEquals("Q25485/hero.webp", state.topSpecies[0].heroImagePath)
            assertNull(state.topSpecies[1].heroImagePath)
        }

    @Test
    fun `first sightings of the year are numbered in time order with name and date and photo`() =
        runTest(dispatcher) {
            val repo = FakeObservationRepository()
            // Q25485 first in Jan (again in May), Q25234 first in Mar, Q25404 first in May.
            repo.seedObservation("Q25485", Instant.parse("2026-05-10T08:00:00Z"))
            repo.seedObservation("Q25404", Instant.parse("2026-05-15T08:00:00Z"))
            repo.seedObservation("Q25234", Instant.parse("2026-03-14T08:00:00Z"))
            repo.seedObservation("Q25485", Instant.parse("2026-01-08T08:00:00Z"))
            val vm =
                SeasonStatsViewModel(
                    observationRepo = repo,
                    speciesRepo = speciesWithHeroFor("Q25485"),
                    clock = fixedClock("2026-12-22T08:00:00Z"),
                    zone = utc,
                    locale = Locale.SV,
                )
            vm.onEnter()
            val state = vm.state.value as SeasonStatsUiState.Loaded
            assertEquals(
                listOf(
                    SeasonStatsUiState.FirstSightingRow(
                        qid = "Q25485",
                        nameLocalized = "Talgoxe",
                        heroImagePath = "Q25485/hero.webp",
                        month = 1,
                        dayOfMonth = 8,
                        number = 1,
                    ),
                    SeasonStatsUiState.FirstSightingRow(
                        qid = "Q25234",
                        nameLocalized = "Koltrast",
                        heroImagePath = null,
                        month = 3,
                        dayOfMonth = 14,
                        number = 2,
                    ),
                    SeasonStatsUiState.FirstSightingRow(
                        qid = "Q25404",
                        nameLocalized = "Blåmes",
                        heroImagePath = null,
                        month = 5,
                        dayOfMonth = 15,
                        number = 3,
                    ),
                ),
                state.firstSightings,
            )
        }

    @Test
    fun `the first sighting date and the month bars follow the time zone`() =
        runTest(dispatcher) {
            val repo = FakeObservationRepository()
            // 23:30 UTC on 31 January is 1 February in Stockholm.
            repo.seedObservation("Q1", Instant.parse("2026-01-31T23:30:00Z"))
            val vm =
                SeasonStatsViewModel(
                    observationRepo = repo,
                    speciesRepo = FakeSpeciesRepository(),
                    clock = fixedClock("2026-05-22T08:00:00Z"),
                    zone = TimeZone.of("Europe/Stockholm"),
                    locale = Locale.SV,
                )
            vm.onEnter()
            val state = vm.state.value as SeasonStatsUiState.Loaded
            assertEquals(2, state.firstSightings.single().month)
            assertEquals(1, state.firstSightings.single().dayOfMonth)
            assertEquals(1, state.monthBars.first { it.month == 2 }.observationCount)
            assertEquals(0, state.monthBars.first { it.month == 1 }.observationCount)
        }

    @Test
    fun `loaded state carries the year and the best month and the current month`() =
        runTest(dispatcher) {
            val repo = FakeObservationRepository()
            repo.seedObservation("Q1", Instant.parse("2026-01-10T08:00:00Z"))
            repo.seedObservation("Q1", Instant.parse("2026-05-10T08:00:00Z"))
            repo.seedObservation("Q2", Instant.parse("2026-05-11T08:00:00Z"))
            val vm =
                SeasonStatsViewModel(
                    observationRepo = repo,
                    speciesRepo = FakeSpeciesRepository(),
                    clock = fixedClock("2026-10-05T08:00:00Z"),
                    zone = utc,
                    locale = Locale.SV,
                )
            vm.onEnter()
            val state = vm.state.value as SeasonStatsUiState.Loaded
            assertEquals(2026, state.year)
            assertEquals(5, state.bestMonth)
            assertEquals(10, state.currentMonth)
        }

    @Test
    fun `a tie for the most finds leaves the best month empty`() =
        runTest(dispatcher) {
            val repo = FakeObservationRepository()
            repo.seedObservation("Q1", Instant.parse("2026-01-10T08:00:00Z"))
            repo.seedObservation("Q2", Instant.parse("2026-03-10T08:00:00Z"))
            val vm =
                SeasonStatsViewModel(
                    observationRepo = repo,
                    speciesRepo = FakeSpeciesRepository(),
                    clock = fixedClock("2026-10-05T08:00:00Z"),
                    zone = utc,
                    locale = Locale.SV,
                )
            vm.onEnter()
            assertNull((vm.state.value as SeasonStatsUiState.Loaded).bestMonth)
        }

    @Test
    fun `swedish locale uses MAJ and OKT abbreviations`() =
        runTest(dispatcher) {
            val repo = FakeObservationRepository()
            repo.seedObservation("Q1", Instant.parse("2026-05-12T08:00:00Z"))
            val vm =
                SeasonStatsViewModel(
                    observationRepo = repo,
                    speciesRepo = FakeSpeciesRepository(),
                    clock = fixedClock("2026-05-22T08:00:00Z"),
                    zone = utc,
                    locale = Locale.SV,
                )
            vm.onEnter()
            val state = vm.state.value as SeasonStatsUiState.Loaded
            assertEquals("MAJ", state.monthBars.first { it.month == 5 }.label)
            assertEquals("OKT", state.monthBars.first { it.month == 10 }.label)
        }

    /** The default five species, with a plate photo (role "hero") for [qid] only. */
    private fun speciesWithHeroFor(qid: String): FakeSpeciesRepository =
        FakeSpeciesRepository.withDefaults().apply {
            byId.value =
                byId.value.mapValues { (id, species) ->
                    if (id.raw == qid) {
                        species?.copy(
                            images =
                                listOf(
                                    SpeciesImage(
                                        role = "hero",
                                        path = "$qid/hero.webp",
                                        width = 800,
                                        height = 600,
                                        license = "CC BY-SA 4.0",
                                        author = "Test",
                                        sourceUrl = "",
                                    ),
                                ),
                        )
                    } else {
                        species
                    }
                }
        }

    private fun fixedClock(iso: String): Clock {
        val instant = Instant.parse(iso)
        return object : Clock {
            override fun now(): Instant = instant
        }
    }
}
