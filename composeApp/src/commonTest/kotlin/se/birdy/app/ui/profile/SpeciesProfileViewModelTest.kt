package se.birdy.app.ui.profile

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import se.birdy.app.testing.FakeSpeciesRepository
import se.birdy.content.Abundance
import se.birdy.content.Locale
import se.birdy.content.SpeciesId
import se.birdy.content.model.Species
import se.birdy.content.model.SpeciesTaxonomy
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class SpeciesProfileViewModelTest {
    @BeforeTest
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    private val talgoxe =
        Species(
            id = SpeciesId("Q25485"),
            scientificName = "Parus major",
            taxonomy =
                SpeciesTaxonomy(
                    family = "Paridae",
                    familySv = "Mesfåglar",
                    genus = "Parus",
                    iocOrder = "Passeriformes",
                ),
            name = "Talgoxe",
            abundance = Abundance.ALLMÄN,
            iucnStatus = "LC",
            regions = listOf("SE", "NO", "FI", "DK", "DE"),
            season =
                listOf("jan", "feb", "mar", "apr", "maj", "jun", "jul", "aug", "sep", "okt", "nov", "dec")
                    .associateWith { "present" },
            description = "Talgoxen är en av Sveriges vanligaste tättingar.",
            migration = "Mestadels stannfågel.",
            images = emptyList(),
        )

    @Test
    fun loadedStateExposesSpeciesAfterRepoEmits() =
        runTest {
            val repo =
                FakeSpeciesRepository().apply {
                    byId.value = mapOf(SpeciesId("Q25485") to talgoxe)
                }
            val vm = SpeciesProfileViewModel(repo, SpeciesId("Q25485"), Locale.SV)
            vm.uiState.test {
                assertEquals(SpeciesProfileUiState.Loaded(talgoxe), awaitItem())
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun unknownIdEmitsNotFoundState() =
        runTest {
            val repo = FakeSpeciesRepository()
            val vm = SpeciesProfileViewModel(repo, SpeciesId("Q99999999"), Locale.SV)
            vm.uiState.test {
                assertEquals(SpeciesProfileUiState.NotFound, awaitItem())
                cancelAndConsumeRemainingEvents()
            }
        }
}
