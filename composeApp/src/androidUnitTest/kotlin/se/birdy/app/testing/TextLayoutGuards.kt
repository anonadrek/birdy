package se.birdy.app.testing

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.text.TextLayoutResult
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import se.birdy.app.ui.components.hasForcedMidWordBreak

/**
 * T12d Important 1 / T12e Minor 1 (moved here from StatsScreenshotTest in release 1.3.0 Task 7c so
 * the normal test gate runs it too, not only the opt-in screenshot suite): a generic guard for the
 * whole composed screen. It walks every node that carries a `GetTextLayoutResult` semantics action
 * (i.e. every laid-out Text/BasicText), fetches its real [TextLayoutResult] and checks three ways
 * a label can silently lose content:
 * 1. [hasForcedMidWordBreak]: the wrap broke mid-word rather than at a space (the message lists the
 *    lines as laid out).
 * 2. A single-line node (`maxLines == 1`) exceeded that line and got clipped
 *    (`multiParagraph.didExceedMaxLines`).
 * 3. A `softWrap = false` node needs more width for its text (`multiParagraph.maxIntrinsicWidth`)
 *    than the box Compose actually gave it. Neither `hasVisualOverflow` (true for nearly all
 *    ordinary Text) nor `multiParagraph.width` works here: in a fixed-size box
 *    `multiParagraph.width` equals `size.width`, so it never fails (T11f/T12e re-review).
 *
 * `useUnmergedTree = true` so a merged container (e.g. a row with `mergeDescendants = true`)
 * doesn't hide a child's own layout node. Text drawn on a Canvas is not a semantics node and is
 * not covered here; the year ring's geometry has its own tests (YearRingGeometryTest).
 */
internal fun ComposeContentTestRule.assertNoTextLayoutRegressions() {
    val layoutResults = mutableListOf<TextLayoutResult>()
    val nodes =
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
            .fetchSemanticsNodes()
    assertTrue("expected at least one laid-out text node", nodes.isNotEmpty())
    nodes.forEach { node ->
        val action = node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action ?: return@forEach
        layoutResults.clear()
        action(layoutResults)
        layoutResults.forEach { result ->
            val text = result.layoutInput.text.text
            assertFalse(
                "\"$text\" has a forced mid-word break: " +
                    (0 until result.lineCount).joinToString(" | ") { line ->
                        text.substring(result.getLineStart(line), result.getLineEnd(line, visibleEnd = false))
                    },
                result.hasForcedMidWordBreak(text),
            )
            if (result.layoutInput.maxLines == 1) {
                assertFalse(
                    "\"$text\" exceeded its single line and was clipped",
                    result.multiParagraph.didExceedMaxLines,
                )
            }
            if (!result.layoutInput.softWrap) {
                assertTrue(
                    "\"$text\" needs ${result.multiParagraph.maxIntrinsicWidth}px, " +
                        "more than its softWrap=false box (${result.size.width}px)",
                    result.multiParagraph.maxIntrinsicWidth <= result.size.width,
                )
            }
        }
    }
}
