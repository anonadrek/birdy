package se.birdy.app.ui.diary

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import se.birdy.app.testing.FakeObservationRepository
import se.birdy.app.testing.FakePhotoStorage
import se.birdy.app.testing.FakeSpeciesRepository
import se.birdy.content.Locale
import se.birdy.domain.observation.Observation
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class ObservationDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun before() = Dispatchers.setMain(dispatcher)

    @AfterTest fun after() = Dispatchers.resetMain()

    private fun seed(
        repo: FakeObservationRepository,
        id: String,
        speciesId: String,
    ) {
        runTest {
            repo.insert(
                Observation(
                    id = id,
                    speciesId = speciesId,
                    capturedAt = Instant.parse("2026-05-03T11:08:00Z"),
                    savedAt = Instant.parse("2026-05-03T11:09:00Z"),
                    photoPath = "/p/$id.jpg",
                    note = "ursprunglig",
                    confidence = 0.87f,
                    latitude = null,
                    longitude = null,
                    locationLabel = null,
                ),
            )
        }
    }

    @Test
    fun loaded_emits_observation_and_species() =
        runTest(dispatcher) {
            val obsRepo = FakeObservationRepository()
            seed(obsRepo, "o1", "Q25485")
            val speciesRepo = FakeSpeciesRepository.withDefaults()
            val storage = FakePhotoStorage()
            val vm = ObservationDetailViewModel("o1", obsRepo, speciesRepo, storage, Locale.SV)
            vm.state.test {
                assertIs<ObservationDetailUiState.Loading>(awaitItem())
                val loaded = assertIs<ObservationDetailUiState.Loaded>(awaitItem())
                assertEquals("o1", loaded.observation.id)
                assertEquals("Talgoxe", loaded.species?.name)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun not_found_when_id_missing() =
        runTest(dispatcher) {
            val obsRepo = FakeObservationRepository()
            val speciesRepo = FakeSpeciesRepository.withDefaults()
            val storage = FakePhotoStorage()
            val vm = ObservationDetailViewModel("missing", obsRepo, speciesRepo, storage, Locale.SV)
            vm.state.test {
                assertIs<ObservationDetailUiState.Loading>(awaitItem())
                assertIs<ObservationDetailUiState.NotFound>(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun unknown_species_shows_loaded_with_null_species() =
        runTest(dispatcher) {
            val obsRepo = FakeObservationRepository()
            seed(obsRepo, "o2", "Q_BOGUS")
            val speciesRepo = FakeSpeciesRepository.withDefaults()
            val storage = FakePhotoStorage()
            val vm = ObservationDetailViewModel("o2", obsRepo, speciesRepo, storage, Locale.SV)
            vm.state.test {
                assertIs<ObservationDetailUiState.Loading>(awaitItem())
                val loaded = assertIs<ObservationDetailUiState.Loaded>(awaitItem())
                assertNull(loaded.species)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun save_note_updates_observation() =
        runTest(dispatcher) {
            val obsRepo = FakeObservationRepository()
            seed(obsRepo, "o1", "Q25485")
            val speciesRepo = FakeSpeciesRepository.withDefaults()
            val storage = FakePhotoStorage()
            val vm = ObservationDetailViewModel("o1", obsRepo, speciesRepo, storage, Locale.SV)
            // Drive Loading→Loaded
            vm.state.test {
                awaitItem()
                awaitItem()
                cancelAndIgnoreRemainingEvents()
            }
            vm.saveNote("uppdaterad")
            advanceUntilIdle()
            val updated = obsRepo.observeById("o1").first()!!
            assertEquals("uppdaterad", updated.note)
        }

    @Test
    fun delete_removes_observation_and_photo() =
        runTest(dispatcher) {
            val obsRepo = FakeObservationRepository()
            seed(obsRepo, "o1", "Q25485")
            val speciesRepo = FakeSpeciesRepository.withDefaults()
            val storage = FakePhotoStorage().apply { persisted["/p/o1.jpg"] = ByteArray(8) }
            val vm = ObservationDetailViewModel("o1", obsRepo, speciesRepo, storage, Locale.SV)
            vm.state.test {
                awaitItem()
                awaitItem()
                cancelAndIgnoreRemainingEvents()
            }
            vm.delete()
            advanceUntilIdle()
            assertNull(obsRepo.observeById("o1").first())
            assertEquals(0, storage.persisted.size)
        }
}
