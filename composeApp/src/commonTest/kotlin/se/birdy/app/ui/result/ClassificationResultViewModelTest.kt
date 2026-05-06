package se.birdy.app.ui.result

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
import se.birdy.domain.badge.Badge
import se.birdy.domain.badge.BadgeCatalog
import se.birdy.domain.badge.BadgeCategory
import se.birdy.domain.badge.BadgeRule
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class ClassificationResultViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private val fakeClock = FakeClock(now = Instant.parse("2026-05-06T10:00:00Z"))

    private val saveUseCase =
        SaveObservationUseCase(
            repo = FakeObservationRepository(),
            badgeRepo = FakeBadgeRepository(),
            photoStorage = FakePhotoStorage(),
            clock = fakeClock,
            catalog = BadgeCatalog(version = 1, badges = emptyList()),
            recalculate = RecalculateBadgesUseCase(zone = TimeZone.UTC, clock = fakeClock),
            speciesByQid = { emptyMap() },
        )

    @BeforeTest fun before() = Dispatchers.setMain(dispatcher)

    @AfterTest fun after() = Dispatchers.resetMain()

    @Test
    fun loaded_with_top1_and_runner_ups_when_all_resolve() =
        runTest(dispatcher) {
            val repo = FakeSpeciesRepository.withDefaults()
            val vm =
                ClassificationResultViewModel(
                    repository = repo,
                    saveUseCase = saveUseCase,
                    catalog = BadgeCatalog(version = 1, badges = emptyList()),
                    predictionsCsv = "Q25485:87/100,Q25234:8/100,Q25404:5/100",
                    frameJpegPath = "/cache/scan-frames/x.jpg",
                    capturedAtMs = 1735000000000L,
                    locale = Locale.SV,
                )
            vm.state.test {
                assertIs<ClassificationResultUiState.Loading>(awaitItem())
                val loaded = awaitItem()
                assertIs<ClassificationResultUiState.Loaded>(loaded)
                assertEquals("Q25485", loaded.top1.species.id.raw)
                assertEquals(2, loaded.runnerUps.size)
                assertEquals(0.87f, loaded.top1.confidence, absoluteTolerance = 0.001f)
                assertEquals(0.08f, loaded.runnerUps[0].confidence, absoluteTolerance = 0.001f)
                assertEquals(0.05f, loaded.runnerUps[1].confidence, absoluteTolerance = 0.001f)
                assertEquals("/cache/scan-frames/x.jpg", loaded.frozenFramePath)
                assertEquals(emptyList(), loaded.unresolved)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun unresolved_q_id_appears_in_unresolved_list() =
        runTest(dispatcher) {
            val repo = FakeSpeciesRepository.withDefaults()
            val vm =
                ClassificationResultViewModel(
                    repository = repo,
                    saveUseCase = saveUseCase,
                    catalog = BadgeCatalog(version = 1, badges = emptyList()),
                    predictionsCsv = "Q25485:87/100,Q_BOGUS:5/100",
                    frameJpegPath = null,
                    capturedAtMs = 1735000000000L,
                    locale = Locale.SV,
                )
            vm.state.test {
                assertIs<ClassificationResultUiState.Loading>(awaitItem())
                val loaded = awaitItem()
                assertIs<ClassificationResultUiState.Loaded>(loaded)
                assertEquals("Q25485", loaded.top1.species.id.raw)
                assertEquals(0, loaded.runnerUps.size)
                assertEquals(listOf("Q_BOGUS"), loaded.unresolved)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun all_unresolved_emits_error() =
        runTest(dispatcher) {
            val repo = FakeSpeciesRepository.withDefaults()
            val vm =
                ClassificationResultViewModel(
                    repository = repo,
                    saveUseCase = saveUseCase,
                    catalog = BadgeCatalog(version = 1, badges = emptyList()),
                    predictionsCsv = "Q_BOGUS1:50/100,Q_BOGUS2:30/100",
                    frameJpegPath = null,
                    capturedAtMs = 1735000000000L,
                    locale = Locale.SV,
                )
            vm.state.test {
                assertIs<ClassificationResultUiState.Loading>(awaitItem())
                val err = awaitItem()
                assertIs<ClassificationResultUiState.Error>(err)
                assertEquals(ClassificationResultUiState.Error.Kind.NoMatches, err.kind)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun saveToDiary_enqueues_new_unlocks_and_disables_CTA_until_dismissed() =
        runTest(dispatcher) {
            val noviceBadge =
                Badge(
                    id = "novice",
                    category = BadgeCategory.PROGRESSION,
                    rule = BadgeRule.CountUniqueSpecies(target = 1),
                )
            val catalog = BadgeCatalog(version = 1, badges = listOf(noviceBadge))
            val observationRepo = FakeObservationRepository()
            val badgeRepo = FakeBadgeRepository()
            val photoStorage = FakePhotoStorage()
            val clock = FakeClock(now = Instant.parse("2026-05-07T10:00:00Z"))
            val speciesRepo = FakeSpeciesRepository.withDefaults()
            val saveUseCase =
                SaveObservationUseCase(
                    repo = observationRepo,
                    badgeRepo = badgeRepo,
                    photoStorage = photoStorage,
                    clock = clock,
                    catalog = catalog,
                    recalculate = RecalculateBadgesUseCase(clock = clock, zone = TimeZone.UTC),
                    speciesByQid = { speciesRepo.allByQid() },
                )

            // Write a real temp file so File(path).readBytes() succeeds in the VM.
            val tmpFile = File.createTempFile("birdy-test-frame", ".jpg")
            tmpFile.writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x01))
            val framePath = tmpFile.absolutePath

            val vm =
                ClassificationResultViewModel(
                    repository = speciesRepo,
                    saveUseCase = saveUseCase,
                    catalog = catalog,
                    predictionsCsv = "Q25485:87/100",
                    frameJpegPath = framePath,
                    capturedAtMs = 1735000000000L,
                    locale = Locale.SV,
                )

            vm.state.test {
                // Skip Loading
                var item = awaitItem()
                while (item is ClassificationResultUiState.Loading) item = awaitItem()

                // Initial Loaded — no pending unlock
                val initial = item as ClassificationResultUiState.Loaded
                assertEquals(0, initial.unlockQueueSize)
                assertNull(initial.pendingUnlock)

                vm.saveToDiary()

                // Wait for state with unlock enqueued and status Saved
                var seen: ClassificationResultUiState.Loaded? = null
                while (true) {
                    val n = awaitItem() as? ClassificationResultUiState.Loaded ?: continue
                    if (n.unlockQueueSize > 0 && n.saveStatus == ClassificationResultUiState.SaveStatus.Saved) {
                        seen = n
                        break
                    }
                }
                val afterSave = seen!!
                assertEquals(1, afterSave.unlockQueueSize)
                assertEquals("novice", afterSave.pendingUnlock?.badgeId)
                assertEquals("novice", afterSave.pendingBadge?.id)

                vm.dismissUnlock()

                // Wait for queue to drain
                var afterDismiss: ClassificationResultUiState.Loaded? = null
                while (true) {
                    val n = awaitItem() as? ClassificationResultUiState.Loaded ?: continue
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
}
