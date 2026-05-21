package se.birdy.app.ui.photoanalyze

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import se.birdy.app.di.AppGraph
import se.birdy.ml.FrameFormat
import se.birdy.ml.ImageInput
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

@Composable
actual fun PhotoAnalyzeHost(
    graph: AppGraph,
    onLoaded: (sourceJson: String, capturedAtMs: Long) -> Unit,
) {
    val context = LocalContext.current
    val photoCacheDir =
        androidx.compose.runtime.remember(context) {
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

    // #5: use rememberSaveable so the URI survives config-change / process death
    val pendingTakeUri =
        rememberSaveable(stateSaver = uriSaver()) {
            mutableStateOf<Uri?>(null)
        }

    // #1: URI to decode off main thread; set via launcher callback, consumed by LaunchedEffect
    val pendingDecodeUri =
        androidx.compose.runtime.remember {
            mutableStateOf<Uri?>(null)
        }

    // #1: Move decode/scale/encode to IO thread; surface Analyzing immediately
    LaunchedEffect(pendingDecodeUri.value) {
        val uri = pendingDecodeUri.value ?: return@LaunchedEffect
        viewModel.markAnalyzing()
        val input = decodeAndScale(context, uri)
        if (input == null) {
            // #2: surface DecodeFailure instead of falling through to TooSmall
            viewModel.decodeFailed()
        } else {
            viewModel.analyze(input)
        }
        pendingDecodeUri.value = null
    }

    val galleryLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia(),
        ) { uri ->
            if (uri != null) pendingDecodeUri.value = uri
        }
    val takePhotoLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.TakePicture(),
        ) { success ->
            val uri = pendingTakeUri.value
            if (success && uri != null) pendingDecodeUri.value = uri
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

// #5: Saver to keep pendingTakeUri across config-change / process death
private fun uriSaver(): Saver<Uri?, String> =
    Saver(
        save = { it?.toString() ?: "" },
        restore = { if (it.isEmpty()) null else Uri.parse(it) },
    )

// #1: Heavy decode/scale/encode runs on Dispatchers.IO
private suspend fun decodeAndScale(
    context: android.content.Context,
    uri: Uri,
): ImageInput? =
    withContext(Dispatchers.IO) {
        // #2: null stream → DecodeFailure (caller handles null return)
        val bytes =
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext null

        // #2: null decode → DecodeFailure
        val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext null

        val rotation = readExifRotation(context, uri)

        // #3: recycle intermediate bitmaps to avoid native-memory pressure
        val rotated =
            if (rotation != 0) {
                val m = Matrix().apply { postRotate(rotation.toFloat()) }
                Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true).also {
                    if (it !== raw) raw.recycle()
                }
            } else {
                raw
            }

        val (w, h) = scaleToLongSide(rotated.width, rotated.height, target = 1024)
        val scaled =
            Bitmap.createScaledBitmap(rotated, w, h, true).also {
                if (it !== rotated) rotated.recycle()
            }

        val baos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 90, baos)
        // #3: recycle after compress
        scaled.recycle()

        ImageInput(
            bytes = baos.toByteArray(),
            widthPx = w,
            heightPx = h,
            format = FrameFormat.JPEG,
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
