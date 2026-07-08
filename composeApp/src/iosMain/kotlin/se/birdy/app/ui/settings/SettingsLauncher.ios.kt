package se.birdy.app.ui.settings

import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication

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
    val root = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return
    val controller = UIActivityViewController(activityItems = items, applicationActivities = null)
    root.presentViewController(controller, animated = true, completion = null)
}
