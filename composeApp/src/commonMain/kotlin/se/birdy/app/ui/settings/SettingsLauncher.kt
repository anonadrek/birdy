package se.birdy.app.ui.settings

expect fun openExternalUrl(url: String)

expect fun openMailto(
    address: String,
    subject: String,
)

expect fun shareApp(text: String)

expect fun openPlayStoreListing(packageName: String)
