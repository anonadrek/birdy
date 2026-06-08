package se.birdy.app.ui.match

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import se.birdy.app.badges.RecalculateBadgesUseCase
import se.birdy.app.location.LatLng
import se.birdy.app.testing.FakeBadgeRepository
import se.birdy.app.testing.FakeClock
import se.birdy.app.testing.FakeLocationProvider
import se.birdy.app.testing.FakeObservationRepository
import se.birdy.app.testing.FakePhotoStorage
import se.birdy.app.testing.FakeSpeciesRepository
import se.birdy.app.usecase.SaveObservationUseCase
import se.birdy.content.Locale
import se.birdy.domain.badge.Badge
import se.birdy.domain.badge.BadgeCatalog
import se.birdy.domain.badge.BadgeCategory
import se.birdy.domain.badge.BadgeRule
import se.birdy.ml.Classification
import se.birdy.ml.ClassificationResult
import se.birdy.ml.ImageOrigin
import se.birdy.ml.ScanSource
import java.io.File
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
            speciesByQid = { speciesRepo.allByQid(Locale.SV) },
        )

    /**
     * Converts a legacy CSV string like "Q25485:87/100,Q25234:8/100" into a
     * [ScanSource.Image] so existing test scenarios continue to work without change.
     */
    private fun csvToScanSource(
        predictionsCsv: String,
        frameJpegPath: String = "/cache/scan-frames/x.jpg",
    ): ScanSource {
        val results =
            predictionsCsv.split(",").mapNotNull { entry ->
                val parts = entry.split(":")
                if (parts.size != 2) return@mapNotNull null
                val id = parts[0].trim()
                val confParts = parts[1].split("/")
                val numerator = confParts.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
                val denominator = confParts.getOrNull(1)?.toIntOrNull() ?: 100
                if (id.isBlank() || denominator <= 0 || numerator < 0) return@mapNotNull null
                val conf = (numerator.toFloat() / denominator.toFloat()).coerceIn(0f, 1f)
                ClassificationResult(id, conf)
            }
        return ScanSource.Image(
            frameJpegPath = frameJpegPath,
            classification = Classification(results = results),
        )
    }

    private fun makeVm(
        predictionsCsv: String,
        obsRepo: FakeObservationRepository = FakeObservationRepository(),
        speciesRepo: FakeSpeciesRepository = FakeSpeciesRepository.withDefaults(),
        frameJpegPath: String = "/cache/scan-frames/x.jpg",
    ) = MatchResultViewModel(
        repository = speciesRepo,
        observationRepo = obsRepo,
        saveUseCase = makeSaveUseCase(obsRepo, speciesRepo),
        catalog = emptyCatalog(),
        source = csvToScanSource(predictionsCsv, frameJpegPath),
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
    fun resolve_audio_source_empty_results_returns_nobird_not_error() =
        runTest(dispatcher) {
            val audioSource =
                ScanSource.Audio(
                    frameJpegPath = "/cache/audio/1234.png",
                    classification = Classification(results = emptyList(), frameTimestampMillis = 1234L),
                    audioWavPath = "/cache/audio/1234.opus",
                )
            val vm =
                MatchResultViewModel(
                    repository = FakeSpeciesRepository.withDefaults(),
                    observationRepo = FakeObservationRepository(),
                    saveUseCase = makeSaveUseCase(),
                    catalog = emptyCatalog(),
                    source = audioSource,
                    capturedAtMs = capturedAtMs,
                    locale = Locale.SV,
                )
            vm.state.test {
                assertIs<MatchResultUiState.Loading>(awaitItem())
                val nobird = awaitItem()
                assertIs<MatchResultUiState.NoBird>(nobird)
                assertEquals("/cache/audio/1234.png", nobird.frameJpegPath)
                assertEquals(capturedAtMs, nobird.capturedAtMs)
                assertNull(nobird.topPrediction)
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

    @Test
    fun pickFromDisambig_known_species_transitions_to_match_manual() =
        runTest(dispatcher) {
            val vm = makeVm("Q25485:42/100,Q25234:40/100,Q25404:36/100")
            vm.state.test {
                assertIs<MatchResultUiState.Loading>(awaitItem())
                val disambig = awaitItem()
                assertIs<MatchResultUiState.Disambig>(disambig)
                vm.pickFromDisambig(disambig.candidates[1].species.id)
                val match = awaitItem()
                assertIs<MatchResultUiState.Match>(match)
                assertEquals("Q25234", match.species.id.raw)
                assertTrue(match.isManualPick)
                assertEquals(disambig.stampNumber, match.stampNumber)
                assertEquals(disambig.frameJpegPath, match.frameJpegPath)
                assertEquals(disambig.capturedAtMs, match.capturedAtMs)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun pickFromDisambig_unknown_species_id_is_noop() =
        runTest(dispatcher) {
            val vm = makeVm("Q25485:42/100,Q25234:40/100")
            vm.state.test {
                assertIs<MatchResultUiState.Loading>(awaitItem())
                val disambig = awaitItem()
                assertIs<MatchResultUiState.Disambig>(disambig)
                vm.pickFromDisambig(se.birdy.content.SpeciesId("Q_NOT_IN_LIST"))
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun pickFromDisambig_called_on_non_disambig_state_is_noop() =
        runTest(dispatcher) {
            val vm = makeVm("Q25485:87/100")
            vm.state.test {
                assertIs<MatchResultUiState.Loading>(awaitItem())
                val match = awaitItem()
                assertIs<MatchResultUiState.Match>(match)
                vm.pickFromDisambig(se.birdy.content.SpeciesId("Q25485"))
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun saveToDiary_on_nobird_state_is_noop() =
        runTest(dispatcher) {
            val vm = makeVm("Q25485:20/100")
            vm.state.test {
                assertIs<MatchResultUiState.Loading>(awaitItem())
                assertIs<MatchResultUiState.NoBird>(awaitItem())
                vm.saveToDiary()
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun saveToDiary_gallery_source_uses_exif_preset_location() =
        runTest(dispatcher) {
            val obsRepo = FakeObservationRepository()
            val speciesRepo = FakeSpeciesRepository.withDefaults()
            val clock = FakeClock(now = Instant.parse("2026-05-12T10:00:00Z"))
            val provider = FakeLocationProvider(next = LatLng(59.3, 18.0))
            val saveUseCase =
                SaveObservationUseCase(
                    repo = obsRepo,
                    badgeRepo = FakeBadgeRepository(),
                    photoStorage = FakePhotoStorage(),
                    clock = clock,
                    catalog = emptyCatalog(),
                    recalculate = RecalculateBadgesUseCase(zone = TimeZone.UTC, clock = clock),
                    speciesByQid = { speciesRepo.allByQid(Locale.SV) },
                    locationProvider = provider,
                    locationEnabled = { true },
                )

            val tmpFile = File.createTempFile("birdy-test-frame", ".jpg")
            tmpFile.writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x01))

            val gallerySource =
                ScanSource.Image(
                    frameJpegPath = tmpFile.absolutePath,
                    classification = Classification(results = listOf(ClassificationResult("Q25485", 0.87f))),
                    origin = ImageOrigin.Gallery,
                    exifLatitude = 40.0,
                    exifLongitude = -3.0,
                )
            val vm =
                MatchResultViewModel(
                    repository = speciesRepo,
                    observationRepo = obsRepo,
                    saveUseCase = saveUseCase,
                    catalog = emptyCatalog(),
                    source = gallerySource,
                    capturedAtMs = capturedAtMs,
                    locale = Locale.SV,
                )

            vm.state.test {
                var item = awaitItem()
                while (item is MatchResultUiState.Loading) item = awaitItem()
                assertIs<MatchResultUiState.Match>(item)
                vm.saveToDiary()
                while (true) {
                    val n = awaitItem() as? MatchResultUiState.Match ?: continue
                    if (n.saveStatus == MatchResultUiState.SaveStatus.Saved) break
                }
                cancelAndIgnoreRemainingEvents()
            }

            val row = obsRepo.observeAll().first().single()
            assertEquals(40.0, row.latitude)
            assertEquals(-3.0, row.longitude)
            assertEquals(0, provider.currentCalls)
            tmpFile.delete()
        }

    @Test
    fun saveToDiary_on_match_success_transitions_to_saved_and_enqueues_unlocks() =
        runTest(dispatcher) {
            val noviceBadge =
                Badge(
                    id = "novice",
                    category = BadgeCategory.PROGRESSION,
                    rule = BadgeRule.CountUniqueSpecies(target = 1),
                )
            val catalog = BadgeCatalog(version = 1, badges = listOf(noviceBadge))
            val obsRepo = FakeObservationRepository()
            val speciesRepo = FakeSpeciesRepository.withDefaults()
            val clock = FakeClock(now = Instant.parse("2026-05-12T10:00:00Z"))
            val saveUseCase =
                SaveObservationUseCase(
                    repo = obsRepo,
                    badgeRepo = FakeBadgeRepository(),
                    photoStorage = FakePhotoStorage(),
                    clock = clock,
                    catalog = catalog,
                    recalculate = RecalculateBadgesUseCase(zone = TimeZone.UTC, clock = clock),
                    speciesByQid = { speciesRepo.allByQid(Locale.SV) },
                )

            val tmpFile = File.createTempFile("birdy-test-frame", ".jpg")
            tmpFile.writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x01))

            val vm =
                MatchResultViewModel(
                    repository = speciesRepo,
                    observationRepo = obsRepo,
                    saveUseCase = saveUseCase,
                    catalog = catalog,
                    source = csvToScanSource("Q25485:87/100", tmpFile.absolutePath),
                    capturedAtMs = capturedAtMs,
                    locale = Locale.SV,
                )

            vm.state.test {
                var item = awaitItem()
                while (item is MatchResultUiState.Loading) item = awaitItem()
                val initial = item as MatchResultUiState.Match
                assertEquals(0, initial.unlockQueueSize)
                assertNull(initial.pendingUnlock)

                vm.saveToDiary()

                var seen: MatchResultUiState.Match? = null
                while (true) {
                    val n = awaitItem() as? MatchResultUiState.Match ?: continue
                    if (n.unlockQueueSize > 0 && n.saveStatus == MatchResultUiState.SaveStatus.Saved) {
                        seen = n
                        break
                    }
                }
                val afterSave = seen!!
                assertEquals(1, afterSave.unlockQueueSize)
                assertEquals("novice", afterSave.pendingUnlock?.badgeId)

                vm.dismissUnlock()

                var afterDismiss: MatchResultUiState.Match? = null
                while (true) {
                    val n = awaitItem() as? MatchResultUiState.Match ?: continue
                    if (n.unlockQueueSize == 0) {
                        afterDismiss = n
                        break
                    }
                }
                assertEquals(0, afterDismiss!!.unlockQueueSize)
                assertNull(afterDismiss.pendingUnlock)

                cancelAndIgnoreRemainingEvents()
            }

            tmpFile.delete()
        }

    @Test
    fun saveToDiary_on_match_with_null_frame_transitions_to_failed_frame_unavailable() =
        runTest(dispatcher) {
            val obsRepo = FakeObservationRepository()
            val speciesRepo = FakeSpeciesRepository.withDefaults()
            // Use empty frameJpegPath to trigger FrameUnavailable (blank string → null in VM)
            val vm =
                MatchResultViewModel(
                    repository = speciesRepo,
                    observationRepo = obsRepo,
                    saveUseCase = makeSaveUseCase(obsRepo, speciesRepo),
                    catalog = emptyCatalog(),
                    source = csvToScanSource("Q25485:87/100", ""),
                    capturedAtMs = capturedAtMs,
                    locale = Locale.SV,
                )
            vm.state.test {
                var item = awaitItem()
                while (item is MatchResultUiState.Loading) item = awaitItem()
                assertIs<MatchResultUiState.Match>(item)
                vm.saveToDiary()
                val after = awaitItem()
                assertIs<MatchResultUiState.Match>(after)
                val status = after.saveStatus
                assertIs<MatchResultUiState.SaveStatus.Failed>(status)
                assertEquals(MatchResultUiState.SaveStatus.Failed.Kind.FrameUnavailable, status.kind)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
