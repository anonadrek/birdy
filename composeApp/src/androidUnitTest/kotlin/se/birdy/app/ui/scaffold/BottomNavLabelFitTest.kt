package se.birdy.app.ui.scaffold

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.layout.LayoutInfo
import androidx.compose.ui.platform.InspectableValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import se.birdy.app.testing.attachComposeResourcesContext
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Release 1.3.0 Plan 3 Task 7 (device walkthrough, API 36 emulator): with the system text size at
 * 1.3 the selected "Uppslagsverk" tab label lost its first and last letters, at 1.5 it read
 * "Uppslagsve" and at 2.0 "Identifiera" and "Mina arter" were cut too. Two causes: the label could
 * be wider than its fifth of the bar, and the tab clipped its whole content to a pill (corner
 * radius half its height), whose round ends cut into the label row whenever a label came close to
 * the tab's width. Every label must stay on one line, fit its tab and never sit under a clip.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "sv-w411dp-h891dp-xxhdpi")
class BottomNavLabelFitTest {
    @get:Rule
    val compose = createComposeRule()

    // Plan 3 Task 7 review: the label must not be taller than its share of the tab either, or it
    // is cut at the bottom (seen at text size 2.0 on the emulator).
    private val labelsSv = listOf("Identifiera", "Uppslagsverk", "Mina arter", "Märken", "Karta")

    @Before
    fun setUp() {
        attachComposeResourcesContext()
    }

    /** The bar with Archive (the longest label, bold when selected) as the current tab. */
    private fun showBarOnArchive() {
        compose.setContent {
            val nav = rememberNavController()
            Column {
                NavHost(navController = nav, startDestination = AppRoute.Archive) {
                    composable<AppRoute.Archive> {}
                }
                BottomNavBar(nav)
            }
        }
        compose.waitForIdle()
    }

    private fun layoutOf(label: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose
            .onNodeWithText(label, useUnmergedTree = true)
            .fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult]
            .action
            ?.invoke(results)
        return results.single()
    }

    private fun assertEveryLabelFits() {
        for (label in labelsSv) {
            val layout = layoutOf(label)
            val needed = layout.multiParagraph.intrinsics.maxIntrinsicWidth
            val available = layout.layoutInput.constraints.maxWidth
            assertEquals(1, layout.lineCount, "'$label' broke onto ${layout.lineCount} lines")
            assertTrue(needed <= available, "'$label' needs $needed px on one line but its tab has $available")
            assertTrue(!layout.didOverflowHeight, "'$label' is taller than its share of the tab: ${layout.getLineBottom(0)} px")
        }
    }

    /**
     * Plan 3 Task 7 review: the label's line height was in sp, so at text size 1.5 and up it grew
     * past the tab's 48 dp column (icon, label, dot) and squeezed the selected tab's dot away.
     */
    private fun assertSelectionDotsKeepTheirSize() {
        compose
            .onAllNodesWithTag(SELECTED_DOT_TAG, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .also { assertEquals(labelsSv.size, it.size) }
            .forEach { node ->
                val heightDp = with(compose.density) { node.size.height.toDp() }
                assertTrue(heightDp >= 3.9.dp, "a selection dot is squeezed to $heightDp")
            }
    }

    /** No layout from a label up to its tab (the merged semantics node) may clip its content. */
    private fun assertNoLabelIsClipped() {
        for (label in labelsSv) {
            val tab = compose.onNodeWithText(label).fetchSemanticsNode().layoutInfo
            var layout: LayoutInfo? = compose.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode().layoutInfo
            while (layout != null) {
                val clips =
                    layout.getModifierInfo().map { it.modifier }.filter { modifier ->
                        val inspectable = modifier as? InspectableValue
                        inspectable?.nameFallback == "graphicsLayer" &&
                            inspectable.inspectableElements.any { it.name == "clip" && it.value == true }
                    }
                assertTrue(clips.isEmpty(), "'$label' sits in a layout that clips it")
                layout = if (layout == tab) null else layout.parentInfo
            }
        }
    }

    @Test
    fun `every tab label fits and is not clipped at the default text size`() {
        showBarOnArchive()
        assertEveryLabelFits()
        assertNoLabelIsClipped()
    }

    @Test
    @Config(fontScale = 1.3f)
    fun `every tab label fits and is not clipped at text size 1_3`() {
        showBarOnArchive()
        assertEveryLabelFits()
        assertNoLabelIsClipped()
    }

    @Test
    @Config(fontScale = 1.5f)
    fun `every tab label fits and the dot keeps its size at text size 1_5`() {
        showBarOnArchive()
        assertEveryLabelFits()
        assertNoLabelIsClipped()
        assertSelectionDotsKeepTheirSize()
    }

    @Test
    @Config(fontScale = 2.0f)
    fun `every tab label fits and is not clipped at text size 2_0`() {
        showBarOnArchive()
        assertEveryLabelFits()
        assertNoLabelIsClipped()
        assertSelectionDotsKeepTheirSize()
    }

    @Test
    @Config(fontScale = 2.0f, qualifiers = "sv-w360dp-h740dp-xxhdpi")
    fun `every tab label fits and the dot keeps its size on a 360 dp phone at text size 2_0`() {
        showBarOnArchive()
        assertEveryLabelFits()
        assertNoLabelIsClipped()
        assertSelectionDotsKeepTheirSize()
    }
}
