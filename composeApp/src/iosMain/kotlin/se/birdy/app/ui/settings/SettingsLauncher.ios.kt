package se.birdy.app.ui.settings

import platform.Foundation.NSLog
import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
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

/**
 * StoreKit 2 purchases (and a real "manage subscription" sheet) land in plan i5 — today every
 * iOS build runs on a permanent Active(LIFETIME) override (see IosAppGraph's premiumOverride
 * KDoc), which has no Play-style sku to manage, so this row never actually shows on iOS yet.
 * Wired honestly anyway: Apple's own subscriptions page, not a claim of in-app management.
 */
actual fun openManageSubscription(sku: String) = openExternalUrl("https://apps.apple.com/account/subscriptions")

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
    root.presentViewController(controller, animated = true, completion = null)
}
