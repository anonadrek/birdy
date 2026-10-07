package se.birdy.app.ui.match

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.datetime.TimeZone
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import se.birdy.app.testing.StatusBarInset
import se.birdy.app.testing.attachComposeResourcesContext
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
import kotlin.test.assertTrue

/**
 * "Spara observation" is Match's main action and must be in view without scrolling, also with
 * larger text (2026-10-06: the photo above the name made the hero taller). A 360x800dp phone
 * as the app really shows it: a 32dp status bar the hero draws behind, and below the screen the
 * app's 72dp bottom bar plus a 48dp three-button navigation bar (the tallest kind).
 *
 * NATIVE graphics, so the text has its real metrics (the default fake ones measured the content
 * below the photo ~35dp short, 2026-10-07 review). The photo credit is one of the longest in
 * species.db, so it takes two lines.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "sv-w360dp-h800dp-xhdpi")
class MatchSaveButtonFoldTest {
    @get:Rule
    val compose = createComposeRule()

    @Before
    fun setUp() {
        attachComposeResourcesContext()
    }

    private val robin =
        Species(
            id = SpeciesId("Q25334"),
            scientificName = "Erithacus rubecula",
            taxonomy =
                SpeciesTaxonomy(family = "Muscicapidae", familySv = "Flugsnappare", genus = "Erithacus", iocOrder = "Passeriformes"),
            name = "Rödhake",
            abundance = Abundance.ALLMÄN,
            iucnStatus = "LC",
            regions = listOf("SE"),
            season = emptyMap(),
            description = null,
            migration = null,
            images =
                listOf(
                    SpeciesImage(
                        "hero",
                        "Q25334/hero.webp",
                        2400,
                        1800,
                        "Public domain",
                        "U.S. Fish and Wildlife Service - Midwest Region",
                        "https://commons.wikimedia.org/wiki/File:Robin.jpg",
                    ),
                ),
        )

    private class Fold(
        val buttonOverflow: Dp,
        val photoHeight: Dp,
    )

    private fun layOut(fontScale: Float): Fold {
        RuntimeEnvironment.setFontScale(fontScale)
        val state =
            MatchResultUiState.Match(
                species = robin,
                confidence = 0.94f,
                isManualPick = false,
                isFirstSighting = true,
                prevObservedAt = null,
                sightingCount = 1,
                stampNumber = 12,
                frameJpegPath = null,
                capturedAtMs = 1_800_000_000_000L,
                source = ScanSource.Image("/fake/frame.jpg", Classification(listOf(ClassificationResult("Q25334", 0.94f)))),
            )
        val inset = StatusBarInset(topPx = 64) // 32dp at xhdpi
        compose.setContent {
            inset.Capture()
            BirdyTheme {
                Column {
                    Box(Modifier.weight(1f).fillMaxWidth().testTag("viewport")) {
                        MatchView(state = state, onSave = {}, onCancel = {}, onDismissUnlock = {}, locale = Locale.SV, zone = TimeZone.UTC)
                    }
                    Spacer(Modifier.height(72.dp + 48.dp))
                }
            }
        }
        compose.waitForIdle()
        compose.runOnIdle { inset.apply() }
        compose.waitForIdle()
        val viewport = compose.onNode(hasTestTag("viewport")).getUnclippedBoundsInRoot()
        val button = compose.onNode(hasText("Spara observation")).getUnclippedBoundsInRoot()
        val photo = compose.onNode(hasTestTag(MATCH_PHOTO_TAG)).getUnclippedBoundsInRoot()
        return Fold(buttonOverflow = button.bottom - viewport.bottom, photoHeight = photo.bottom - photo.top)
    }

    private fun assertSaveButtonInView(fontScale: Float) {
        val fold = layOut(fontScale)
        assertTrue(
            fold.buttonOverflow <= 0.dp,
            "at font scale $fontScale the save button ends ${fold.buttonOverflow} below the visible area " +
                "(photo ${fold.photoHeight})",
        )
    }

    /**
     * Larger text on this small phone: what follows the photo is too tall for the button to fit
     * above the fold, so the photo keeps its floor rather than being cut to a strip, and the button
     * is a short scroll away. Measured 2026-10-07 with this two-line credit: 44dp at 130%, 239dp
     * at 200%; at 130% even without a credit the photo was already at its floor here.
     */
    private fun assertPhotoKeepsItsFloor(fontScale: Float) {
        val fold = layOut(fontScale)
        // The photo includes the 32dp status bar it draws behind.
        val floor = MATCH_PHOTO_MIN + 32.dp
        if (fold.buttonOverflow > 0.dp) {
            assertEquals(floor, fold.photoHeight, "the button is ${fold.buttonOverflow} below the fold, so the photo must be at its floor")
        }
        assertTrue(fold.photoHeight >= floor, "photo ${fold.photoHeight} below the floor")
    }

    @Test
    fun `the save button is in view at normal text size`() = assertSaveButtonInView(1f)

    @Test
    fun `at 130 percent text the photo keeps its floor`() = assertPhotoKeepsItsFloor(1.3f)

    @Test
    fun `at 200 percent text the photo keeps its floor`() = assertPhotoKeepsItsFloor(2f)

    // A common phone size (412x915dp, e.g. Pixel 7 and 8) has the room at 130% text.
    @Test
    @Config(qualifiers = "sv-w412dp-h915dp-xhdpi")
    fun `the save button is in view at 130 percent text on a common phone`() = assertSaveButtonInView(1.3f)
}
