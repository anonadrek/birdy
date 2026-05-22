package se.birdy.app.ui.encyclopedia

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import se.birdy.app.testing.FakeObservationRepository
import se.birdy.app.testing.FakeSpeciesRepository
import se.birdy.app.testing.FakeUserPreferences
import se.birdy.content.Locale
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
}
