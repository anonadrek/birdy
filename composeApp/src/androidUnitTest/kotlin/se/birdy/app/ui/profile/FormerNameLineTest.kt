package se.birdy.app.ui.profile

import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import se.birdy.app.testing.FakeSpeciesRepository
import se.birdy.app.testing.attachComposeResourcesContext
import se.birdy.app.ui.theme.BirdyTheme
import se.birdy.content.Abundance
import se.birdy.content.Locale
import se.birdy.content.SpeciesId
import se.birdy.content.model.Species
import se.birdy.content.model.SpeciesTaxonomy

/**
 * Release 1.3.0 Task 7m: Birdy took BirdLife Sverige's official Swedish names, so Sädgås became
 * Skogsgås. The profile says what the species was called before, under its name, and only for
 * the renamed species. That English users see no line is up to the repository, which gives no
 * former name in English (SpeciesRepositoryTest).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class FormerNameLineTest {
    @get:Rule
    val compose = createComposeRule()

    private fun skogsgas(formerName: String?) =
        Species(
            id = SpeciesId("Q26452"),
            scientificName = "Anser fabalis",
            taxonomy = SpeciesTaxonomy(family = "Anatidae", familySv = "Andfåglar", genus = "Anser", iocOrder = "Anseriformes"),
            name = "Skogsgås",
            abundance = Abundance.OVANLIG,
            iucnStatus = "LC",
            regions = listOf("SE"),
            season = emptyMap(),
            description = "Skogsgåsen häckar i nordlig barrskog.",
            migration = null,
            images = emptyList(),
            formerName = formerName,
        )

    private fun show(
        species: Species,
        locale: Locale,
    ) {
        attachComposeResourcesContext()
        compose.setContent {
            BirdyTheme {
                SpeciesProfileScreen(
                    viewModel =
                        remember {
                            SpeciesProfileViewModel(
                                repo = FakeSpeciesRepository().apply { byId.value = mapOf(species.id to species) },
                                speciesId = species.id,
                                locale = locale,
                            )
                        },
                    locale = locale,
                    onBack = {},
                    onPremiumClick = {},
                    showPremiumTeaser = false,
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `a renamed species shows its former name under the name`() {
        show(skogsgas(formerName = "Sädgås"), Locale.SV)
        compose.onNodeWithText("Skogsgås").assertExists()
        compose.onNodeWithText("Tidigare: Sädgås").assertExists()
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `a species that kept its name shows no former name`() {
        show(skogsgas(formerName = null), Locale.SV)
        compose.onNodeWithText("Skogsgås").assertExists()
        compose.onNodeWithText("Tidigare:", substring = true).assertDoesNotExist()
    }
}
