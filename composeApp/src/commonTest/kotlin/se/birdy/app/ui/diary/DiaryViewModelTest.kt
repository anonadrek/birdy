package se.birdy.app.ui.diary

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import se.birdy.app.testing.FakeClock
import se.birdy.app.testing.FakeObservationRepository
import se.birdy.app.testing.FakeSpeciesRepository
import se.birdy.content.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class DiaryViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val tz = TimeZone.of("Europe/Stockholm")
    private val clock = FakeClock(now = Instant.parse("2026-05-09T12:00:00Z"))

    @BeforeTest fun before() = Dispatchers.setMain(dispatcher)

    @AfterTest fun after() = Dispatchers.resetMain()

    @Test
    fun empty_state_when_no_rows() =
        runTest(dispatcher) {
            val obsRepo = FakeObservationRepository()
            val speciesRepo = FakeSpeciesRepository.withDefaults()
            val vm = DiaryViewModel(obsRepo, speciesRepo, Locale.SV, clock, tz)
            vm.state.test {
                assertIs<DiaryUiState.Loading>(awaitItem())
                assertIs<DiaryUiState.Empty>(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun loaded_groups_observations_by_month_desc() =
        runTest(dispatcher) {
            val obsRepo = FakeObservationRepository.withDefaults()
            val speciesRepo = FakeSpeciesRepository.withDefaults()
            val vm = DiaryViewModel(obsRepo, speciesRepo, Locale.SV, clock, tz)
            vm.state.test {
                assertIs<DiaryUiState.Loading>(awaitItem())
                val loaded = assertIs<DiaryUiState.Loaded>(awaitItem())
                assertEquals(2, loaded.months.size)
                // Senaste månad (May 2026) först
                assertEquals(2026 to 5, loaded.months[0].year to loaded.months[0].month1Based)
                assertEquals(2026 to 4, loaded.months[1].year to loaded.months[1].month1Based)
                assertEquals(4, loaded.months[0].items.size) // 4 obs i maj
                assertEquals(1, loaded.months[1].items.size) // 1 obs i april
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun item_uses_localized_species_name_and_confidence_pct() =
        runTest(dispatcher) {
            val obsRepo = FakeObservationRepository.withDefaults()
            val speciesRepo = FakeSpeciesRepository.withDefaults()
            val vm = DiaryViewModel(obsRepo, speciesRepo, Locale.SV, clock, tz)
            vm.state.test {
                assertIs<DiaryUiState.Loading>(awaitItem())
                val loaded = assertIs<DiaryUiState.Loaded>(awaitItem())
                val first = loaded.months[0].items[0]
                assertEquals("Talgoxe", first.speciesName) // Q25485 i sv
                assertEquals(87, first.confidencePct)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun unknown_species_falls_back_to_placeholder_name() =
        runTest(dispatcher) {
            val obsRepo = FakeObservationRepository()
            val ts = clock.now.toEpochMilliseconds()
            obsRepo.insert(
                se.birdy.domain.observation.Observation(
                    id = "x",
                    speciesId = "Q_UNKNOWN",
                    capturedAt = Instant.fromEpochMilliseconds(ts - 1_000),
                    savedAt = Instant.fromEpochMilliseconds(ts),
                    photoPath = "/p.jpg",
                    note = "",
                    confidence = 0.5f,
                    latitude = null,
                    longitude = null,
                    locationLabel = null,
                ),
            )
            val speciesRepo = FakeSpeciesRepository.withDefaults()
            val vm = DiaryViewModel(obsRepo, speciesRepo, Locale.SV, clock, tz)
            vm.state.test {
                assertIs<DiaryUiState.Loading>(awaitItem())
                val loaded = assertIs<DiaryUiState.Loaded>(awaitItem())
                assertEquals(DiaryItem.UNKNOWN_SPECIES_PLACEHOLDER, loaded.months[0].items[0].speciesName)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
