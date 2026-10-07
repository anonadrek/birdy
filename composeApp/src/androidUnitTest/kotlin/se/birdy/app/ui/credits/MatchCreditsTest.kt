package se.birdy.app.ui.credits

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.LinkAnnotation
import kotlinx.datetime.TimeZone
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import se.birdy.app.testing.attachComposeResourcesContext
import se.birdy.app.ui.match.DisambigView
import se.birdy.app.ui.match.MatchResultUiState
import se.birdy.app.ui.match.MatchView
import se.birdy.app.ui.match.ResolvedPrediction
import se.birdy.app.ui.theme.BirdyTheme
import se.birdy.content.Abundance
import se.birdy.content.Locale
import se.birdy.content.SpeciesId
import se.birdy.content.model.Species
import se.birdy.content.model.SpeciesImage
import se.birdy.content.model.SpeciesTaxonomy
import se.birdy.ml.Classification
import se.birdy.ml.ClassificationResult
import se.birdy.ml.ScanSource
import kotlin.test.assertEquals

/**
 * Release 1.3.0 Task 7e-2 (legal review §2 recommends it): Match and Disambig show a large or
 * clickable species photo, so they credit it too, in one line: the photographer and the licence.
 * On Match "Foto: X" opens the photo's Commons page and the licence its deed; on Disambig the
 * whole card is the button that picks the species, so its credit has no links of its own.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "sv-w411dp-h891dp-xxhdpi")
class MatchCreditsTest {
    @get:Rule
    val compose = createComposeRule()

    private fun species(
        id: String,
        name: String,
        image: SpeciesImage,
    ) = Species(
        id = SpeciesId(id),
        scientificName = "Turdus merula",
        taxonomy = SpeciesTaxonomy(family = "Turdidae", familySv = "Trastar", genus = "Turdus", iocOrder = "Passeriformes"),
        name = name,
        abundance = Abundance.ALLMÄN,
        iucnStatus = "LC",
        regions = listOf("SE"),
        season = emptyMap(),
        description = null,
        migration = null,
        images = listOf(image),
    )

    private val koltrast =
        species(
            "Q25234",
            "Koltrast",
            SpeciesImage("hero", "Q25234/hero.webp", 1, 1, "CC BY-SA 4.0", "Musicaline", "", "Turdus merula 1294.jpg"),
        )
    private val talgoxe =
        species("Q25485", "Talgoxe", SpeciesImage("hero", "Q25485/hero.webp", 1, 1, "CC0", "Hobbyfotowiki", "", "Great tit.jpg"))

    private val source = ScanSource.Image("/fake/frame.jpg", Classification(listOf(ClassificationResult("Q25234", 0.94f))))

    @Test
    fun `match credits its photo under it, the photographer opening the source`() {
        attachComposeResourcesContext()
        val state =
            MatchResultUiState.Match(
                species = koltrast,
                confidence = 0.94f,
                isManualPick = false,
                isFirstSighting = true,
                prevObservedAt = null,
                sightingCount = 1,
                stampNumber = 12,
                frameJpegPath = null,
                capturedAtMs = 1_800_000_000_000L,
                source = source,
            )
        compose.setContent {
            BirdyTheme {
                MatchView(state = state, onSave = {}, onCancel = {}, onDismissUnlock = {}, locale = Locale.SV, zone = TimeZone.UTC)
            }
        }
        compose.waitForIdle()
        val credit = compose.onNodeWithText(nb("Foto: Musicaline · CC BY-SA 4.0"))
        val text = credit.fetchSemanticsNode().config[SemanticsProperties.Text].single()
        assertEquals(
            listOf(
                "Foto: Musicaline" to "https://commons.wikimedia.org/wiki/File:Turdus_merula_1294.jpg",
                "CC BY-SA 4.0" to "https://creativecommons.org/licenses/by-sa/4.0/",
            ),
            text.getLinkAnnotations(0, text.length).map {
                text.substring(it.start, it.end).replace(NO_BREAK_SPACE, ' ') to (it.item as LinkAnnotation.Url).url
            },
        )
        credit.onChildren().assertCountEquals(2)
        credit.onChildren()[0].assertHasClickAction()
        // Under the photo: the credit ends above the kicker, which starts the text below the photo.
        val creditBottom = credit.fetchSemanticsNode().boundsInRoot.bottom
        val kickerTop =
            compose
                .onNodeWithText("MATCH · NO 12")
                .fetchSemanticsNode()
                .boundsInRoot.top
        kotlin.test.assertTrue(creditBottom <= kickerTop, "credit ($creditBottom) runs into the kicker ($kickerTop)")
        // ...but TalkBack reads it after the name.
        assertCreditReadAfter(compose.onNodeWithText(nb("Foto: Musicaline · CC BY-SA 4.0"), useUnmergedTree = true), title = "Koltrast")
    }

    @Test
    fun `each disambig candidate credits its photo, inside the card and without links`() {
        attachComposeResourcesContext()
        val state =
            MatchResultUiState.Disambig(
                candidates = listOf(ResolvedPrediction(koltrast, 0.55f), ResolvedPrediction(talgoxe, 0.30f)),
                stampNumber = 12,
                frameJpegPath = null,
                capturedAtMs = 1_800_000_000_000L,
                source = source,
            )
        compose.setContent {
            BirdyTheme {
                DisambigView(state = state, onPick = {}, onSaveAsUnknown = {}, onUnknownSaved = {}, onCancel = {})
            }
        }
        compose.waitForIdle()
        // The card merges its texts into one button, so the credit is read with the species.
        compose
            .onNode(
                hasText("Koltrast", substring = true) and hasText(nb("Foto: Musicaline · CC BY-SA 4.0"), substring = true),
            ).assertHasClickAction()
        compose.onNode(hasText(nb("Foto: Hobbyfotowiki · CC0"), substring = true)).assertHasClickAction()
        for (credit in listOf(nb("Foto: Musicaline · CC BY-SA 4.0"), nb("Foto: Hobbyfotowiki · CC0"))) {
            val node = compose.onNodeWithText(credit, useUnmergedTree = true)
            val text = node.fetchSemanticsNode().config[SemanticsProperties.Text].single()
            assertEquals(0, text.getLinkAnnotations(0, text.length).size, credit)
            node.onChildren().assertCountEquals(0)
        }
    }
}
