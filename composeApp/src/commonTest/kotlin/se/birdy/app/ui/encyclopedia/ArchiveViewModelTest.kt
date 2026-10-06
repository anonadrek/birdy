package se.birdy.app.ui.encyclopedia

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import se.birdy.app.testing.FakeObservationRepository
import se.birdy.app.testing.FakeSpeciesRepository
import se.birdy.app.testing.FakeUserPreferences
import se.birdy.content.Abundance
import se.birdy.content.Locale
import se.birdy.content.SpeciesId
import se.birdy.content.model.SpeciesSummary
import se.birdy.datastore.ArchiveSort
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ArchiveViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest fun setMain() = Dispatchers.setMain(dispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `chip selection persists to DataStore`() =
        runTest(dispatcher) {
            val prefs = FakeUserPreferences()
            val vm =
                ArchiveViewModel(
                    repo = FakeSpeciesRepository(),
                    observationRepo = FakeObservationRepository(),
                    prefs = prefs,
                    locale = Locale.SV,
                    premiumActiveFlow = kotlinx.coroutines.flow.flowOf(false),
                )
            vm.onChipSelected(ArchiveChip.OWLS)
            assertTrue(prefs.archiveChipWrites.contains(ArchiveChip.OWLS.name))
        }

    @Test
    fun `sort cycles alpha to family to recent to alpha`() =
        runTest(dispatcher) {
            val prefs = FakeUserPreferences().apply { archiveSortValue = ArchiveSort.ALPHA }
            val vm =
                ArchiveViewModel(
                    repo = FakeSpeciesRepository(),
                    observationRepo = FakeObservationRepository(),
                    prefs = prefs,
                    locale = Locale.SV,
                    premiumActiveFlow = kotlinx.coroutines.flow.flowOf(false),
                )
            vm.onSortToggle()
            assertEquals(ArchiveSort.FAMILY, prefs.archiveSortValue)
            vm.onSortToggle()
            assertEquals(ArchiveSort.RECENT, prefs.archiveSortValue)
            vm.onSortToggle()
            assertEquals(ArchiveSort.ALPHA, prefs.archiveSortValue)
        }

    // Release 1.3.0 Task 7g item 3: the repository ranks search hits (best match first); the
    // A-Ö sort must not undo that while a query is typed.
    private fun summary(
        name: String,
        family: String,
    ) = SpeciesSummary(
        id = SpeciesId("Q-$name"),
        name = name,
        scientificName = "Testus $name",
        abundance = Abundance.ALLMÄN,
        heroImagePath = null,
        family = family,
    )

    private fun rankedRepo() =
        FakeSpeciesRepository().apply {
            // In best-match order, as SqlDelightSpeciesRepository.search returns them for "tal".
            searchResults.value =
                listOf(
                    summary("Talgoxe", "Paridae"),
                    summary("Taltrast", "Turdidae"),
                    summary("Talltita", "Paridae"),
                    summary("Italiensk sparv", "Passeridae"),
                )
        }

    private fun archiveVm(
        repo: FakeSpeciesRepository,
        sort: ArchiveSort,
    ) = ArchiveViewModel(
        repo = repo,
        observationRepo = FakeObservationRepository(),
        prefs = FakeUserPreferences().apply { archiveSortValue = sort },
        locale = Locale.SV,
        premiumActiveFlow = kotlinx.coroutines.flow.flowOf(false),
    )

    private fun ArchiveViewModel.names(): List<String> = (uiState.value as ArchiveUiState.Loaded).rows.map { it.summary.name }

    @Test
    fun `with a query the a to z sort keeps the best matches first`() =
        runTest(dispatcher) {
            val vm = archiveVm(rankedRepo(), ArchiveSort.ALPHA)
            backgroundScope.launch { vm.uiState.collect {} }
            vm.onQueryChanged("tal")
            advanceTimeBy(300)
            assertEquals(listOf("Talgoxe", "Taltrast", "Talltita", "Italiensk sparv"), vm.names())
        }

    @Test
    fun `without a query the a to z sort is alphabetical`() =
        runTest(dispatcher) {
            val vm = archiveVm(rankedRepo(), ArchiveSort.ALPHA)
            backgroundScope.launch { vm.uiState.collect {} }
            advanceTimeBy(300)
            assertEquals(listOf("Italiensk sparv", "Talgoxe", "Talltita", "Taltrast"), vm.names())
        }

    @Test
    fun `the a to z sort follows the swedish alphabet`() =
        runTest(dispatcher) {
            val repo =
                FakeSpeciesRepository().apply {
                    searchResults.value =
                        listOf("Ökenlärka", "Ärtsångare", "Zebrafink", "Ålgräsfink", "Ägretthäger").map { summary(it, "X") }
                }
            val vm = archiveVm(repo, ArchiveSort.ALPHA)
            backgroundScope.launch { vm.uiState.collect {} }
            advanceTimeBy(300)
            assertEquals(listOf("Zebrafink", "Ålgräsfink", "Ägretthäger", "Ärtsångare", "Ökenlärka"), vm.names())
        }

    @Test
    fun `with a query the family sort groups families and keeps the best matches first in each`() =
        runTest(dispatcher) {
            val vm = archiveVm(rankedRepo(), ArchiveSort.FAMILY)
            backgroundScope.launch { vm.uiState.collect {} }
            vm.onQueryChanged("tal")
            advanceTimeBy(300)
            assertEquals(listOf("Talgoxe", "Talltita", "Italiensk sparv", "Taltrast"), vm.names())
        }
}
