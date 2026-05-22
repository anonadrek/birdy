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
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SeasonStatsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val utc = TimeZone.UTC

    @BeforeTest fun setMain() = Dispatchers.setMain(dispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `empty repo emits Empty after onEnter`() =
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
            assertEquals(SeasonStatsUiState.Empty, vm.state.value)
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
            assertEquals(2, state.seasonDonut.winter)
            assertEquals(1, state.seasonDonut.spring)
            assertEquals(1, state.seasonDonut.summer)
            assertEquals(1, state.seasonDonut.autumn)
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
    fun `cumulative line counts unique species through end-of-month`() =
        runTest(dispatcher) {
            val repo = FakeObservationRepository()
            // Q1 in Jan; Q2 in Mar; Q1 again in May (no new unique); Q3 in May
            repo.seedObservation("Q1", Instant.parse("2026-01-10T08:00:00Z"))
            repo.seedObservation("Q2", Instant.parse("2026-03-10T08:00:00Z"))
            repo.seedObservation("Q1", Instant.parse("2026-05-10T08:00:00Z"))
            repo.seedObservation("Q3", Instant.parse("2026-05-15T08:00:00Z"))
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
            assertEquals(12, state.cumulativeLine.size)
            assertEquals(1, state.cumulativeLine.first { it.month == 1 }.uniqueSpeciesByEndOfMonth)
            assertEquals(1, state.cumulativeLine.first { it.month == 2 }.uniqueSpeciesByEndOfMonth)
            assertEquals(2, state.cumulativeLine.first { it.month == 3 }.uniqueSpeciesByEndOfMonth)
            assertEquals(2, state.cumulativeLine.first { it.month == 4 }.uniqueSpeciesByEndOfMonth)
            assertEquals(3, state.cumulativeLine.first { it.month == 5 }.uniqueSpeciesByEndOfMonth)
            assertEquals(3, state.cumulativeLine.first { it.month == 12 }.uniqueSpeciesByEndOfMonth)
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

    private fun fixedClock(iso: String): Clock {
        val instant = Instant.parse(iso)
        return object : Clock {
            override fun now(): Instant = instant
        }
    }
}
