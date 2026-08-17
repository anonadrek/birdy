package se.birdy.app.ui.settings

expect fun openExternalUrl(url: String)

expect fun openMailto(
    address: String,
    subject: String,
)

expect fun shareApp(text: String)

expect fun openPlayStoreListing(packageName: String)

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
