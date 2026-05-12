package se.birdy.app.ui.match

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import se.birdy.app.badges.RecalculateBadgesUseCase
import se.birdy.app.testing.FakeBadgeRepository
import se.birdy.app.testing.FakeClock
import se.birdy.app.testing.FakeObservationRepository
import se.birdy.app.testing.FakePhotoStorage
import se.birdy.app.testing.FakeSpeciesRepository
import se.birdy.app.usecase.SaveObservationUseCase
import se.birdy.content.Locale
import se.birdy.domain.badge.BadgeCatalog
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MatchResultViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val capturedAtMs = 1735000000000L

    private fun emptyCatalog() = BadgeCatalog(version = 1, badges = emptyList())

    private fun makeSaveUseCase(
        obsRepo: FakeObservationRepository = FakeObservationRepository(),
        speciesRepo: FakeSpeciesRepository = FakeSpeciesRepository.withDefaults(),
        clock: FakeClock = FakeClock(now = Instant.parse("2026-05-12T10:00:00Z")),
    ): SaveObservationUseCase =
        SaveObservationUseCase(
            repo = obsRepo,
            badgeRepo = FakeBadgeRepository(),
            photoStorage = FakePhotoStorage(),
            clock = clock,
            catalog = emptyCatalog(),
            recalculate = RecalculateBadgesUseCase(zone = TimeZone.UTC, clock = clock),
            speciesByQid = { speciesRepo.allByQid() },
        )

    private fun makeVm(
        predictionsCsv: String,
        obsRepo: FakeObservationRepository = FakeObservationRepository(),
        speciesRepo: FakeSpeciesRepository = FakeSpeciesRepository.withDefaults(),
    ) = MatchResultViewModel(
        repository = speciesRepo,
        observationRepo = obsRepo,
        saveUseCase = makeSaveUseCase(obsRepo, speciesRepo),
        catalog = emptyCatalog(),
        predictionsCsv = predictionsCsv,
        frameJpegPath = "/cache/scan-frames/x.jpg",
        capturedAtMs = capturedAtMs,
        locale = Locale.SV,
    )

    @BeforeTest fun before() = Dispatchers.setMain(dispatcher)

    @AfterTest fun after() = Dispatchers.resetMain()

    @Test
    fun resolve_top1_above_match_threshold_first_sighting() =
        runTest(dispatcher) {
            val vm = makeVm("Q25485:87/100,Q25234:8/100,Q25404:5/100")
            vm.state.test {
                assertIs<MatchResultUiState.Loading>(awaitItem())
                val match = awaitItem()
                assertIs<MatchResultUiState.Match>(match)
                assertEquals("Q25485", match.species.id.raw)
                assertEquals(0.87f, match.confidence, absoluteTolerance = 0.001f)
                assertTrue(match.isFirstSighting)
                assertEquals(1, match.sightingCount)
                assertNull(match.prevObservedAt)
                assertEquals(1, match.stampNumber)
                assertEquals(false, match.isManualPick)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun resolve_top1_above_match_threshold_repeat_sighting() =
        runTest(dispatcher) {
            val obsRepo = FakeObservationRepository()
            val earlier = Instant.parse("2026-03-12T08:00:00Z")
            obsRepo.seedObservation("Q25485", earlier, id = "obs-earlier")
            obsRepo.seedObservation("Q25485", Instant.parse("2026-04-01T10:00:00Z"), id = "obs-later")
            val vm = makeVm("Q25485:87/100", obsRepo = obsRepo)
            vm.state.test {
                assertIs<MatchResultUiState.Loading>(awaitItem())
                val match = awaitItem()
                assertIs<MatchResultUiState.Match>(match)
                assertEquals(false, match.isFirstSighting)
                assertEquals(3, match.sightingCount)
                assertEquals(earlier, match.prevObservedAt)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun resolve_top1_in_disambig_band_returns_disambig() =
        runTest(dispatcher) {
            val vm = makeVm("Q25485:42/100,Q25234:40/100,Q25404:36/100")
            vm.state.test {
                assertIs<MatchResultUiState.Loading>(awaitItem())
                val disambig = awaitItem()
                assertIs<MatchResultUiState.Disambig>(disambig)
                assertEquals(3, disambig.candidates.size)
                assertEquals(1, disambig.stampNumber)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun resolve_disambig_filters_candidates_below_threshold() =
        runTest(dispatcher) {
            val vm = makeVm("Q25485:42/100,Q25234:36/100,Q25404:10/100")
            vm.state.test {
                assertIs<MatchResultUiState.Loading>(awaitItem())
                val disambig = awaitItem()
                assertIs<MatchResultUiState.Disambig>(disambig)
                assertEquals(2, disambig.candidates.size)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun resolve_top1_below_nobird_threshold_returns_nobird() =
        runTest(dispatcher) {
            val vm = makeVm("Q25485:20/100,Q25234:15/100")
            vm.state.test {
                assertIs<MatchResultUiState.Loading>(awaitItem())
                val nobird = awaitItem()
                assertIs<MatchResultUiState.NoBird>(nobird)
                assertEquals("/cache/scan-frames/x.jpg", nobird.frameJpegPath)
                assertEquals(capturedAtMs, nobird.capturedAtMs)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun resolve_empty_predictions_returns_error() =
        runTest(dispatcher) {
            val vm = makeVm("")
            vm.state.test {
                assertIs<MatchResultUiState.Loading>(awaitItem())
                val err = awaitItem()
                assertIs<MatchResultUiState.Error>(err)
                assertEquals(MatchResultUiState.Error.Kind.NoPredictions, err.kind)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun resolve_all_unresolved_returns_error_parse_failed() =
        runTest(dispatcher) {
            val vm = makeVm("Q_BOGUS1:87/100,Q_BOGUS2:5/100")
            vm.state.test {
                assertIs<MatchResultUiState.Loading>(awaitItem())
                val err = awaitItem()
                assertIs<MatchResultUiState.Error>(err)
                assertEquals(MatchResultUiState.Error.Kind.ParseFailed, err.kind)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
