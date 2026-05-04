package se.birdy.app.ui.encyclopedia

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import se.birdy.app.testing.FakeSpeciesRepository
import se.birdy.content.Abundance
import se.birdy.content.Locale
import se.birdy.content.SpeciesId
import se.birdy.content.model.SpeciesSummary
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class EncyclopediaViewModelTest {
    @BeforeTest
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    private val talgoxe =
        SpeciesSummary(
            id = SpeciesId("Q25485"),
            name = "Talgoxe",
            scientificName = "Parus major",
            abundance = Abundance.ALLMÄN,
            heroImagePath = null,
        )

    private val balkanmes =
        SpeciesSummary(
            id = SpeciesId("Q574281"),
            name = "Balkanmes",
            scientificName = "Poecile lugubris",
            abundance = Abundance.OVANLIG,
            heroImagePath = null,
        )

    @Test
    fun defaultStateGroupsAllmännaAboveÖvriga() =
        runTest {
            val repo =
                FakeSpeciesRepository().apply {
                    allList.value = listOf(talgoxe, balkanmes)
                    searchResults.value = listOf(talgoxe, balkanmes)
                }
            val vm = EncyclopediaViewModel(repo, Locale.SV)
            vm.uiState.test {
                assertEquals(EncyclopediaUiState.Loading, awaitItem())
                val loaded = awaitItem() as EncyclopediaUiState.Loaded
                assertEquals(listOf(talgoxe), loaded.grouped.common)
                assertEquals(listOf(balkanmes), loaded.grouped.others)
                assertEquals("Allmänna i Sverige", loaded.sectionCommonHeader)
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun searchQueryDebounces250msBeforeTriggeringRepo() =
        runTest {
            val repo =
                FakeSpeciesRepository().apply {
                    searchResults.value = listOf(talgoxe)
                }
            val vm = EncyclopediaViewModel(repo, Locale.SV)
            vm.uiState.test {
                assertEquals(EncyclopediaUiState.Loading, awaitItem())
                awaitItem() // initial Loaded with no query

                vm.onQueryChanged("T")
                advanceTimeBy(100)
                assertEquals(null, repo.lastSearchCall?.first?.takeIf { it == "T" })
                advanceTimeBy(200)
                assertEquals("T", repo.lastSearchCall?.first)
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun emptySearchResultEmitsEmptyState() =
        runTest {
            val repo =
                FakeSpeciesRepository().apply {
                    searchResults.value = emptyList()
                }
            val vm = EncyclopediaViewModel(repo, Locale.SV)
            vm.uiState.test {
                assertEquals(EncyclopediaUiState.Loading, awaitItem())
                vm.onQueryChanged("zzz")
                advanceTimeBy(300)
                var current = awaitItem()
                while (current !is EncyclopediaUiState.Empty) current = awaitItem()
                assertEquals(EncyclopediaUiState.Empty, current)
                cancelAndConsumeRemainingEvents()
            }
        }
}
