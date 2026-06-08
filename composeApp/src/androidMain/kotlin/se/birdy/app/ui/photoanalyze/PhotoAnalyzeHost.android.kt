package se.birdy.app.ui.photoanalyze

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import se.birdy.app.di.AppGraph
import se.birdy.ml.FrameFormat
import se.birdy.ml.ImageInput
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

private const val CROP_WORKING_MAX_PX = 2048
private const val ANALYZE_LONG_SIDE_PX = 1024
private const val MIN_SHORT_SIDE_PX = 224

@Composable
actual fun PhotoAnalyzeHost(
    graph: AppGraph,
    onLoaded: (sourceJson: String, capturedAtMs: Long) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
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

    val pendingTakeUri =
        rememberSaveable(stateSaver = uriSaver()) {
            mutableStateOf<Uri?>(null)
        }
    val pendingDecodeUri =
        remember {
            mutableStateOf<Uri?>(null)
        }
    // Arbets-bitmap som crop-skärmen visar (null = visa picker).
    var cropBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Decode off-main → visa crop (eller analysera direkt om bilden är för liten att beskära).
    LaunchedEffect(pendingDecodeUri.value) {
        val uri = pendingDecodeUri.value ?: return@LaunchedEffect
        viewModel.markAnalyzing()
        val bmp = decodeForCrop(context, uri)
        pendingDecodeUri.value = null
        if (bmp == null) {
            viewModel.decodeFailed()
            return@LaunchedEffect
        }
        if (minOf(bmp.width, bmp.height) < MIN_SHORT_SIDE_PX) {
            // För liten för meningsfull crop → analysera hela (TooSmall fångar).
            val input = withContext(Dispatchers.IO) { finalizeCrop(bmp, CropGeometry.fullRect(bmp.width, bmp.height)) }
            bmp.recycle()
            viewModel.analyze(input)
            return@LaunchedEffect
        }
        viewModel.reset() // dölj "Analyzing" medan crop-skärmen visas
        cropBitmap = bmp
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

    val bmp = cropBitmap
    if (bmp != null) {
        CropAdjustScreen(
            bitmap = bmp,
            onRotate = {
                // Recycla INTE current synkront: CropAdjustScreen håller en ImageBitmap som
                // aliasar samma native-buffer, och recomposition är async → en frame skulle
                // kunna rita en recyclad bitmap (krasch). De mellanliggande roterade
                // bitmapparna är bounded (<=2048px) och GC:as av runtime; den slutliga
                // recyclas i onConfirm efter finalize.
                val current = cropBitmap
                if (current != null) {
                    cropBitmap = rotate90(current)
                }
            },
            onConfirm = { rect ->
                val toFinalize = cropBitmap ?: return@CropAdjustScreen
                cropBitmap = null
                viewModel.markAnalyzing()
                // Avsiktligt på composable-scope, inte viewModelScope: backar användaren ut
                // under det korta finalize-fönstret avbryts crop→analys (önskvärt — annars
                // skulle MatchResult-navigeringen rycka tillbaka dem efter att de lämnat).
                // Källbitmappen GC:as om jobbet avbryts; den recyclas annars efter finalize.
                scope.launch {
                    val input = withContext(Dispatchers.IO) { finalizeCrop(toFinalize, rect) }
                    toFinalize.recycle()
                    viewModel.analyze(input)
                }
            },
            onCancel = {
                // Sätt null först (lämnar CropAdjustScreen); recycla inte synkront — samma
                // alias-skäl som onRotate, bitmappen GC:as.
                cropBitmap = null
                viewModel.reset()
            },
        )
    } else {
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
            onBack = onBack,
        )
    }
}

private fun uriSaver(): Saver<Uri?, String> =
    Saver(
        save = { it?.toString() ?: "" },
        restore = { if (it.isEmpty()) null else Uri.parse(it) },
    )

/** Decode → EXIF-rotera → cap långsida till CROP_WORKING_MAX_PX (OOM-skydd). */
private suspend fun decodeForCrop(
    context: Context,
    uri: Uri,
): Bitmap? =
    withContext(Dispatchers.IO) {
        val bytes =
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val longSide = maxOf(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (longSide / sample > CROP_WORKING_MAX_PX * 2) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }

        val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return@withContext null
        val rotation = readExifRotation(context, uri)
        val rotated =
            if (rotation != 0) {
                val m = Matrix().apply { postRotate(rotation.toFloat()) }
                Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true).also {
                    if (it !== raw) raw.recycle()
                }
            } else {
                raw
            }
        val ls = maxOf(rotated.width, rotated.height)
        if (ls > CROP_WORKING_MAX_PX) {
            val s = CROP_WORKING_MAX_PX.toFloat() / ls
            Bitmap
                .createScaledBitmap(rotated, (rotated.width * s).toInt(), (rotated.height * s).toInt(), true)
                .also { if (it !== rotated) rotated.recycle() }
        } else {
            rotated
        }
    }

/** Beskär enligt rect, skala långsida till ANALYZE_LONG_SIDE_PX, encoda JPEG 90. */
private fun finalizeCrop(
    src: Bitmap,
    rect: CropRect,
): ImageInput {
    val cropped = Bitmap.createBitmap(src, rect.left, rect.top, rect.width, rect.height)
    val (w, h) = scaleToLongSide(cropped.width, cropped.height, target = ANALYZE_LONG_SIDE_PX)
    val scaled = Bitmap.createScaledBitmap(cropped, w, h, true)
    val baos = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, 90, baos)
    // Recycla bara genuint nya intermediär-bitmaps — aldrig src (callern äger och recyclar
    // src). createBitmap/createScaledBitmap kan returnera src oförändrad när crop = hela
    // bilden utan skalning, så jämför identitet före recycle (annars dubbel-recycle av src).
    if (scaled !== src && scaled !== cropped) scaled.recycle()
    if (cropped !== src) cropped.recycle()
    return ImageInput(
        bytes = baos.toByteArray(),
        widthPx = w,
        heightPx = h,
        format = FrameFormat.JPEG,
    )
}

private fun rotate90(src: Bitmap): Bitmap {
    val m = Matrix().apply { postRotate(90f) }
    return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
}

private fun readExifRotation(
    context: Context,
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
