package se.birdy.android.share

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * Plan 6b3 T7: launches an Android share-sheet for the rendered Field Journal PDF.
 *
 * Uses the existing `${applicationId}.fileprovider` (configured in AndroidManifest.xml)
 * to grant a temporary read URI to the chosen consumer (Gmail, Drive, Files…). The
 * `journal_exports/` subdir of cacheDir is registered as a cache-path in
 * res/xml/file_paths.xml.
 */
object JournalPdfShareLauncher {
    fun launch(
        context: Context,
        pdfPath: String,
        chooserTitle: String = "Share Field Journal",
    ) {
        val file = File(pdfPath)
        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, file)
        val sendIntent =
            Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        val chooser = Intent.createChooser(sendIntent, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
