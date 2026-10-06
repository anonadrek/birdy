package se.birdy.app.ui.components

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import se.birdy.app.testing.attachComposeResourcesContext
import kotlin.test.assertEquals

/**
 * Release 1.3.0 Plan 3 Task 7 (device walkthrough, API 36 emulator): the stamp on Match for a
 * species seen before has no name, and TalkBack read it as ",  complete." (Swedish ",  klart.");
 * once saved it read ", unlocked badge.". A stamp without a name is announced by its number.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "sv-w411dp-h891dp-xxhdpi")
class StampSealLabelTest {
    @get:Rule
    val compose = createComposeRule()

    @Before
    fun setUp() {
        attachComposeResourcesContext()
    }

    private fun labelOf(state: StampSealState): String {
        compose.setContent { StampSeal(state = state) }
        compose.waitForIdle()
        return compose
            .onRoot()
            .onChildren()
            .onFirst()
            .fetchSemanticsNode()
            .config[SemanticsProperties.ContentDescription]
            .single()
    }

    @Test
    fun `an unnamed stamp in progress is announced by its number`() {
        assertEquals("Märke nummer 3", labelOf(StampSealState.InProgress(number = 3, name = null, progressLabel = null)))
    }

    @Test
    fun `an unnamed unlocked stamp is announced by its number`() {
        assertEquals("Märke nummer 3", labelOf(StampSealState.Unlocked(number = 3, glyph = null, name = null)))
    }

    @Test
    fun `a named unlocked stamp keeps its name`() {
        assertEquals("Talgoxe, upplåst märke.", labelOf(StampSealState.Unlocked(number = 3, glyph = null, name = "Talgoxe")))
    }

    /**
     * Plan 3 Task 7 review: a locked stamp without a name (onboarding's badge scene, hidden badges
     * in the grid) read ", låst märke. Tryck för mer info." with nothing before the comma.
     */
    @Test
    fun `an unnamed locked stamp is announced without an empty name`() {
        assertEquals("Låst märke.", labelOf(StampSealState.Locked(name = null)))
    }

    @Test
    @Config(qualifiers = "+en")
    fun `an unnamed locked stamp is announced without an empty name in English`() {
        assertEquals("Locked stamp.", labelOf(StampSealState.Locked(name = null)))
    }

    @Test
    fun `a named locked stamp keeps its name`() {
        assertEquals("Talgoxe, låst märke. Tryck för mer info.", labelOf(StampSealState.Locked(name = "Talgoxe")))
    }
}
