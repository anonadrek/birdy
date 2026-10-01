@file:OptIn(ExperimentalForeignApi::class)

package se.birdy.app.ui.settings

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSLog
import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIView
import platform.UIKit.UIViewController
import se.birdy.app.ui.photoanalyze.keyWindowRootViewController

actual fun openExternalUrl(url: String) {
    NSURL.URLWithString(url)?.let { UIApplication.sharedApplication.openURL(it, emptyMap<Any?, Any>(), null) }
}

actual fun openMailto(
    address: String,
    subject: String,
) {
    val encoded = subject.replace(" ", "%20")
    openExternalUrl("mailto:$address?subject=$encoded")
}

actual fun shareApp(text: String) = presentShareSheet(listOf(text))

/** App Store listing does not exist until plan i6 ships; falls back to the website. */
actual fun openPlayStoreListing(packageName: String) = openExternalUrl("https://birdy.community")

actual fun shareJournalPdf(pdfPath: String) {
    presentShareSheet(listOf(NSURL.fileURLWithPath(pdfPath)))
}

private fun presentShareSheet(items: List<*>) {
    val root = keyWindowRootViewController()
    if (root == null) {
        NSLog("Birdy/settings: share sheet dropped — no key window root view controller")
        return
    }
    val controller = UIActivityViewController(activityItems = items, applicationActivities = null)
    // iPad (regular width) presents UIActivityViewController as a popover.
    // TARGETED_DEVICE_FAMILY is 1,2 — without a sourceView/sourceRect Apple
    // throws NSGenericException and the process dies (Settings → Share and
    // Archive → Export PDF). Harmless on iPhone: popover is null and this no-ops.
    anchorActivityPopover(controller, root.view)
    root.presentViewController(controller, animated = true, completion = null)
}

/**
 * Satisfy UIPopoverPresentationController's required anchor before present.
 * sourceRect is the view's center so the popover isn't pinned to (0,0).
 */
internal fun anchorActivityPopover(
    controller: UIViewController,
    anchorView: UIView,
) {
    val popover = controller.popoverPresentationController ?: return
    popover.sourceView = anchorView
    popover.sourceRect =
        anchorView.bounds.useContents {
            CGRectMake(size.width / 2.0, size.height / 2.0, 0.0, 0.0)
        }
}
