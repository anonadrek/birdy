package se.birdy.app.ui.debug

import android.content.Context
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import se.birdy.ml.BirdClassifier
import se.birdy.ml.FrameFormat
import se.birdy.ml.ImageInput
import java.io.File

/**
 * Runs three corpus JPEGs through the BirdClassifier and emits a textual
 * report with per-image:
 *   - decoded dimensions
 *   - mid-row ARGB samples at 8 evenly-spaced x positions
 *   - top-5 predictions sorted by confidence (3 decimals)
 *
 * The report is also persisted to filesDir as
 * `preprocess_dump_${ts}.txt` so it can be pulled via `adb run-as` and
 * diffed against the desktop ml-eval output.
 *
 * Intentional API notes (matches shared/ml/BirdClassifier.kt):
 *  - ImageInput is a data class (NO ImageInput.JpegBytes factory).
 *  - Classification exposes `.results: List<ClassificationResult>`.
 *  - ClassificationResult has `speciesId: String` + `confidence: Float`.
 */
class DiagnosticsRunner(
    private val context: Context,
    private val classifier: BirdClassifier,
    private val photos: List<String> = listOf("talgoxe.jpg", "koltrast.jpg", "blames.jpg"),
) {
    suspend fun run(): String =
        withContext(Dispatchers.IO) {
            val sb = StringBuilder()
            sb.appendLine("Birdy ML preprocess diagnostic")
            sb.appendLine("device=${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            sb.appendLine("ts=${System.currentTimeMillis()}")
            sb.appendLine("photos=${photos.joinToString(", ")}")
            sb.appendLine("---")
            for (photo in photos) {
                sb.appendLine()
                sb.appendLine("=== $photo ===")
                val bytes =
                    try {
                        context.assets.open("benchmark/$photo").use { it.readBytes() }
                    } catch (t: Throwable) {
                        sb.appendLine("ERROR: failed to read asset benchmark/$photo: ${t.message}")
                        continue
                    }
                sb.appendLine("jpeg_bytes=${bytes.size}")

                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (bitmap == null) {
                    sb.appendLine("ERROR: BitmapFactory.decodeByteArray returned null")
                    continue
                }
                sb.appendLine("bitmap=${bitmap.width}x${bitmap.height} config=${bitmap.config}")

                // Sample mid-row ARGB at 8 evenly-spaced x positions.
                val midY = bitmap.height / 2
                val samples =
                    (0 until 8).joinToString(" ") { i ->
                        val x = (bitmap.width * i) / 8
                        val argb = bitmap.getPixel(x, midY)
                        val a = (argb ushr 24) and 0xFF
                        val r = (argb ushr 16) and 0xFF
                        val g = (argb ushr 8) and 0xFF
                        val b = argb and 0xFF
                        "x=$x:[A=$a,R=$r,G=$g,B=$b]"
                    }
                sb.appendLine("mid_row_y=$midY samples=$samples")

                val input =
                    ImageInput(
                        bytes = bytes,
                        widthPx = bitmap.width,
                        heightPx = bitmap.height,
                        rotationDegrees = 0,
                        format = FrameFormat.JPEG,
                        timestampMillis = 0L,
                    )
                val classification =
                    try {
                        classifier.classify(input)
                    } catch (t: Throwable) {
                        sb.appendLine("ERROR: classifier.classify threw: ${t.message}")
                        continue
                    }
                val top5 =
                    classification.results
                        .sortedByDescending { it.confidence }
                        .take(5)
                        .joinToString(", ") { r ->
                            "${r.speciesId}:${"%.3f".format(r.confidence)}"
                        }
                sb.appendLine("top5=$top5")
            }
            sb.appendLine()
            sb.appendLine("---")
            sb.appendLine("end")

            val report = sb.toString()
            val out = File(context.filesDir, "preprocess_dump_${System.currentTimeMillis()}.txt")
            try {
                out.writeText(report)
            } catch (t: Throwable) {
                return@withContext report + "\n(WARN: failed to persist dump: ${t.message})\n"
            }
            report + "\nSaved: ${out.absolutePath}\n"
        }
}
