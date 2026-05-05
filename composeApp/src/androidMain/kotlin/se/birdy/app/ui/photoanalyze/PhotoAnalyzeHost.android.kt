package se.birdy.app.ui.photoanalyze

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.viewmodel.compose.viewModel
import se.birdy.app.di.AppGraph
import se.birdy.ml.FrameFormat
import se.birdy.ml.ImageInput
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

@Composable
actual fun PhotoAnalyzeHost(
    graph: AppGraph,
    onLoaded: (predictionsCsv: String, frameJpegPath: String) -> Unit,
) {
    val context = LocalContext.current
    val photoCacheDir =
        remember(context) {
            File(context.cacheDir, "photo-input").apply { mkdirs() }
        }
    val viewModel =
        viewModel {
            graph.photoAnalyzeViewModel(persist = { bytes ->
                val file = File(photoCacheDir, UUID.randomUUID().toString() + ".jpg")
                file.outputStream().use { it.write(bytes) }
                file.absolutePath
            })
        }

    val pendingTakeUri = remember { mutableStateOf<Uri?>(null) }

    val galleryLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia(),
        ) { uri ->
            if (uri != null) handleUri(context, uri, viewModel)
        }
    val takePhotoLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.TakePicture(),
        ) { success ->
            val uri = pendingTakeUri.value
            if (success && uri != null) handleUri(context, uri, viewModel)
        }

    PhotoAnalyzeScreen(
        viewModel = viewModel,
        onPickFromGallery = {
            galleryLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        },
        onTakePhoto = {
            val file = File(photoCacheDir, UUID.randomUUID().toString() + ".jpg")
            val uri =
                androidx.core.content.FileProvider.getUriForFile(
                    context,
                    context.packageName + ".fileprovider",
                    file,
                )
            pendingTakeUri.value = uri
            takePhotoLauncher.launch(uri)
        },
        onLoaded = onLoaded,
    )
}

private fun handleUri(
    context: android.content.Context,
    uri: Uri,
    viewModel: PhotoAnalyzeViewModel,
) {
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return
    val raw =
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: run {
            viewModel.analyze(
                ImageInput(bytes = byteArrayOf(), widthPx = 0, heightPx = 0, format = FrameFormat.JPEG),
            )
            return
        }
    val rotation = readExifRotation(context, uri)
    val rotated =
        if (rotation != 0) {
            val m = Matrix().apply { postRotate(rotation.toFloat()) }
            Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
        } else {
            raw
        }
    val (w, h) = scaleToLongSide(rotated.width, rotated.height, target = 1024)
    val scaled = Bitmap.createScaledBitmap(rotated, w, h, true)
    val baos = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, 90, baos)
    viewModel.analyze(
        ImageInput(
            bytes = baos.toByteArray(),
            widthPx = scaled.width,
            heightPx = scaled.height,
            format = FrameFormat.JPEG,
        ),
    )
}

private fun readExifRotation(
    context: android.content.Context,
    uri: Uri,
): Int =
    runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            when (
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            ) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } ?: 0
    }.getOrDefault(0)

private fun scaleToLongSide(
    w: Int,
    h: Int,
    target: Int,
): Pair<Int, Int> {
    val long = maxOf(w, h)
    if (long <= target) return w to h
    val ratio = target.toFloat() / long
    return (w * ratio).toInt() to (h * ratio).toInt()
}
