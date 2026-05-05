package se.birdy.app.ui.result

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import se.birdy.app.testing.FakeSpeciesRepository
import se.birdy.content.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class ClassificationResultViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun before() = Dispatchers.setMain(dispatcher)

    @AfterTest fun after() = Dispatchers.resetMain()

    @Test
    fun loaded_with_top1_and_runner_ups_when_all_resolve() =
        runTest(dispatcher) {
            val repo = FakeSpeciesRepository.withDefaults()
            val vm =
                ClassificationResultViewModel(
                    repository = repo,
                    predictionsCsv = "Q25485:87/100,Q25234:8/100,Q25404:5/100",
                    frameJpegPath = "/cache/scan-frames/x.jpg",
                    locale = Locale.SV,
                )
            vm.state.test {
                assertIs<ClassificationResultUiState.Loading>(awaitItem())
                val loaded = awaitItem()
                assertIs<ClassificationResultUiState.Loaded>(loaded)
                assertEquals("Q25485", loaded.top1.species.id.raw)
                assertEquals(2, loaded.runnerUps.size)
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
                    predictionsCsv = "Q25485:87/100,Q_BOGUS:5/100",
                    frameJpegPath = null,
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
                    predictionsCsv = "Q_BOGUS1:50/100,Q_BOGUS2:30/100",
                    frameJpegPath = null,
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
}
