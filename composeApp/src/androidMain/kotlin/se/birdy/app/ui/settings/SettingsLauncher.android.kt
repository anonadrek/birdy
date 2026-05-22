package se.birdy.app.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object SettingsLauncherSetup {
    @Volatile private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    internal fun context(): Context? = appContext
}

private fun Context.startNewTaskActivity(intent: Intent) {
    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

actual fun openExternalUrl(url: String) {
    val ctx = SettingsLauncherSetup.context() ?: return
    ctx.startNewTaskActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}

actual fun openMailto(
    address: String,
    subject: String,
) {
    val ctx = SettingsLauncherSetup.context() ?: return
    val intent =
        Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$address")).apply {
            putExtra(Intent.EXTRA_SUBJECT, subject)
        }
    runCatching { ctx.startNewTaskActivity(intent) }
}

actual fun shareApp(text: String) {
    val ctx = SettingsLauncherSetup.context() ?: return
    val send =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
    ctx.startNewTaskActivity(Intent.createChooser(send, null))
}

actual fun openPlayStoreListing(packageName: String) {
    val ctx = SettingsLauncherSetup.context() ?: return
    val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
    val result = runCatching { ctx.startNewTaskActivity(marketIntent) }
    if (result.isFailure) {
        val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName"))
        runCatching { ctx.startNewTaskActivity(web) }
    }
}

actual fun shareJournalPdf(pdfPath: String) {
    val ctx = SettingsLauncherSetup.context() ?: return
    runCatching {
        val file = File(pdfPath)
        val authority = "${ctx.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(ctx, authority, file)
        val sendIntent =
            Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        val chooser = Intent.createChooser(sendIntent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(chooser)
    }
}
