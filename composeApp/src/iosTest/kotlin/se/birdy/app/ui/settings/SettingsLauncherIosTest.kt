@file:OptIn(ExperimentalForeignApi::class)

package se.birdy.app.ui.settings

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIModalPresentationStyle
import platform.UIKit.UIView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * iPad presents UIActivityViewController as a popover. Apple requires a non-nil
 * sourceView (or barButtonItem) before present — otherwise NSGenericException
 * kills the process. Force popover style so the controller exists on iPhone-sim CI.
 */
class SettingsLauncherIosTest {
    @Test
    fun anchor_sets_source_view_and_centered_rect() {
        val anchor = UIView(frame = CGRectMake(0.0, 0.0, 320.0, 480.0))
        val controller =
            UIActivityViewController(activityItems = listOf("birdy"), applicationActivities = null)
        controller.modalPresentationStyle = UIModalPresentationStyle.UIModalPresentationPopover

        anchorActivityPopover(controller, anchor)

        val popover =
            assertNotNull(
                controller.popoverPresentationController,
                "popover style must create a popoverPresentationController",
            )
        assertEquals(anchor, popover.sourceView, "iPad present requires a non-nil sourceView")
        val (x, y, w, h) =
            popover.sourceRect.useContents {
                listOf(origin.x, origin.y, size.width, size.height)
            }
        assertEquals(160.0, x, 0.01, "sourceRect should be centered horizontally")
        assertEquals(240.0, y, 0.01, "sourceRect should be centered vertically")
        assertEquals(0.0, w, 0.01)
        assertEquals(0.0, h, 0.01)
    }
}
