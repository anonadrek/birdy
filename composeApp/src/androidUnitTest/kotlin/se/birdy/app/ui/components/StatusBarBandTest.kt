package se.birdy.app.ui.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import kotlinx.datetime.TimeZone
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import se.birdy.app.testing.FakeSpeciesRepository
import se.birdy.app.testing.StatusBarInset
import se.birdy.app.testing.attachComposeResourcesContext
import se.birdy.app.ui.match.MatchResultUiState
import se.birdy.app.ui.match.MatchView
import se.birdy.app.ui.profile.SpeciesProfileScreen
import se.birdy.app.ui.profile.SpeciesProfileViewModel
import se.birdy.app.ui.theme.BirdyTheme
import se.birdy.content.Locale
import se.birdy.content.SpeciesId
import se.birdy.ml.Classification
import se.birdy.ml.ClassificationResult
import se.birdy.ml.ScanSource
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Release 1.3.0 Task 7b review: the species profile and Match draw their photo behind the status
 * bar. Once the photo has scrolled away, the page's text ran on under the transparent status bar
 * (over the clock). Now a band in the page colour covers the status bar from that point on, and
 * never while the photo is under it. Robolectric has no status bar, so [StatusBarInset] gives
 * the window a 24dp one.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "sv-w411dp-h891dp-xxhdpi")
class StatusBarBandTest {
    @get:Rule
    val compose = createComposeRule()

    private val inset = StatusBarInset(topPx = 72) // 24dp at xxhdpi

    @Before
    fun setUp() {
        attachComposeResourcesContext()
    }

    private fun scrollTo(y: Float) {
        compose
            .onNode(hasScrollAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
            .performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, y) }
        compose.waitForIdle()
    }

    private fun assertBandCoversTheStatusBar() {
        val band = compose.onNodeWithTag(STATUS_BAR_BAND_TAG).getUnclippedBoundsInRoot()
        assertEquals(0.dp, band.top)
        assertEquals(24.dp, band.bottom)
    }

    @Test
    fun `the profile covers the status bar once the photo has scrolled away, and only then`() {
        val repository =
            FakeSpeciesRepository.withDefaults().apply {
                val id = SpeciesId("Q25485")
                byId.value = byId.value + (id to byId.value.getValue(id)!!.copy(description = "Talgoxen sjunger. ".repeat(200)))
            }
        val viewModel = SpeciesProfileViewModel(repository, SpeciesId("Q25485"), Locale.SV)
        val backdrop = StatusBarBackdrop()
        compose.setContent {
            inset.Capture()
            CompositionLocalProvider(LocalStatusBarBackdrop provides backdrop) {
                BirdyTheme { SpeciesProfileScreen(viewModel = viewModel, locale = Locale.SV, onBack = {}, onPremiumClick = {}) }
            }
        }
        compose.waitForIdle()
        compose.runOnIdle { inset.apply() }
        compose.waitForIdle()

        compose.onNodeWithTag(STATUS_BAR_BAND_TAG).assertDoesNotExist()
        compose.runOnIdle { assertTrue(backdrop.anyDark, "light icons over the photo") }
        // The photo is 280dp (840px) below the status bar. 10px of it left: nothing over it yet.
        scrollTo(830f)
        compose.onNodeWithTag(STATUS_BAR_BAND_TAG).assertDoesNotExist()
        compose.runOnIdle { assertTrue(backdrop.anyDark) }
        // Past the photo: the band, and dark icons on it, at the same point.
        scrollTo(20f)
        assertBandCoversTheStatusBar()
        compose.runOnIdle { assertFalse(backdrop.anyDark, "dark icons on the band") }

        scrollTo(100_000f)
        assertBandCoversTheStatusBar()

        scrollTo(-100_000f)
        compose.onNodeWithTag(STATUS_BAR_BAND_TAG).assertDoesNotExist()
        compose.runOnIdle { assertTrue(backdrop.anyDark) }
    }

    /**
     * A phone on its side is where Match can scroll its photo away: about 300dp of the window's
     * height is left between the status bar and the bottom bar.
     */
    @Test
    @Config(qualifiers = "sv-w800dp-h300dp-xxhdpi")
    fun `Match covers the status bar once the photo has scrolled away`() {
        val species =
            FakeSpeciesRepository
                .withDefaults()
                .byId.value
                .getValue(SpeciesId("Q25485"))!!
        val state =
            MatchResultUiState.Match(
                species = species,
                confidence = 0.94f,
                isManualPick = false,
                isFirstSighting = true,
                prevObservedAt = null,
                sightingCount = 1,
                stampNumber = 12,
                frameJpegPath = null,
                capturedAtMs = 1_800_000_000_000L,
                source = ScanSource.Image("/fake/frame.jpg", Classification(listOf(ClassificationResult("Q25485", 0.94f)))),
            )
        compose.setContent {
            inset.Capture()
            BirdyTheme {
                MatchView(state = state, onSave = {}, onCancel = {}, onDismissUnlock = {}, locale = Locale.SV, zone = TimeZone.UTC)
            }
        }
        compose.waitForIdle()
        compose.runOnIdle { inset.apply() }
        compose.waitForIdle()

        compose.onNodeWithTag(STATUS_BAR_BAND_TAG).assertDoesNotExist()
        scrollTo(100_000f)
        assertBandCoversTheStatusBar()
        scrollTo(-100_000f)
        compose.onNodeWithTag(STATUS_BAR_BAND_TAG).assertDoesNotExist()
    }
}
