package se.birdy.app.ui.settings

expect fun openExternalUrl(url: String)

expect fun openMailto(
    address: String,
    subject: String,
)

expect fun shareApp(text: String)

expect fun openPlayStoreListing(packageName: String)

/**
 * Opens an online, easy-to-use place to manage or cancel the given subscription SKU
 * (Google Play policy 9900533: account settings must link to the Play subscription center).
 * Android actual reads the running app's own package name off the platform Context, never a
 * hardcoded one, so the link always points at whichever build is actually running (e.g. a
 * debug build's `.debug`-suffixed id). iOS has no real subscriptions yet (StoreKit lands in
 * plan i5); its actual opens Apple's own subscriptions page rather than claiming an in-app
 * management flow Birdy does not have.
 */
expect fun openManageSubscription(sku: String)

/**
 * Plan 6b3 T8: hand the rendered Field Journal PDF off to the platform share-sheet.
 *
 * Android actual builds a FileProvider URI under `${applicationId}.fileprovider`
 * (matches `res/xml/file_paths.xml` cache-path "journal-exports") and fires
 * `ACTION_SEND` with `FLAG_GRANT_READ_URI_PERMISSION` so Gmail/Drive/Files can
 * read the cached PDF. JVM actual is a no-op; iOS presents UIActivityViewController
 * (sedan i2b).
 */
expect fun shareJournalPdf(pdfPath: String)
