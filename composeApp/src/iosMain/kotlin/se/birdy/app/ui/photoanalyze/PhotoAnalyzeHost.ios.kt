package se.birdy.app.ui.photoanalyze

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import se.birdy.app.di.AppGraph

private const val MIN_SHORT_SIDE_PX = 224

/**
 * iOS-host för foto-ID. Speglar Android-hostens tillståndsmaskin
 * (PhotoAnalyzeHost.android.kt): PHPicker → avkoda → (crop | hoppa över) → analysera.
 * Endast galleri i i2b — ta-foto (kamera) landar i i2c, så [PhotoAnalyzeScreen]s
 * take-photo-knapp får en no-op och är present-men-inaktiv tills dess.
 */
@Composable
actual fun PhotoAnalyzeHost(
    graph: AppGraph,
    onLoaded: (sourceJson: String, capturedAtMs: Long) -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val viewModel =
        remember(graph) {
            graph.photoAnalyzeViewModel(persist = { bytes -> persistToCaches(bytes) })
        }

    // Bytes från senaste PHPicker-valet (null = inget nytt val); levereras på main-tråden.
    val pendingBytes = remember { mutableStateOf<ByteArray?>(null) }
    // Arbetsbild som crop-skärmen visar (null = visa picker). Motsvarar Androids cropBitmap.
    var cropWorkingImage by remember { mutableStateOf<WorkingImage?>(null) }

    // Avkoda off-main → visa crop (eller analysera direkt om bilden är för liten att beskära).
    LaunchedEffect(pendingBytes.value) {
        val bytes = pendingBytes.value ?: return@LaunchedEffect
        viewModel.markAnalyzing()
        val working = withContext(Dispatchers.Default) { decodeForCrop(bytes) }
        pendingBytes.value = null
        if (working == null) {
            viewModel.decodeFailed()
            return@LaunchedEffect
        }
        if (minOf(working.width, working.height) < MIN_SHORT_SIDE_PX) {
            // För liten för meningsfull crop → analysera hela (TooSmall fångar).
            val input =
                withContext(Dispatchers.Default) {
                    finalizeCrop(working, CropGeometry.fullRect(working.width, working.height))
                }
            if (input == null) {
                viewModel.decodeFailed()
                return@LaunchedEffect
            }
            viewModel.analyze(input)
            return@LaunchedEffect
        }
        viewModel.reset() // dölj "Analyzing" medan crop-skärmen visas
        cropWorkingImage = working
    }

    val working = cropWorkingImage
    if (working != null) {
        // working.imageBitmap avkodas redan i IosImageDecode.kt:s withContext(Dispatchers.Default)-
        // block (bakeUpright) — komposition läser bara det färdiga värdet, ingen Skia-avkodning här.
        val cropImage = working.imageBitmap
        val cancelCrop = {
            cropWorkingImage = null
            viewModel.reset()
        }
        // CropAdjustScreen hanterar system-back själv (PlatformBackHandler); iOS-actualen är
        // no-op, så avbryt sker här via crop-skärmens Avbryt-knapp (onCancel).
        CropAdjustScreen(
            image = cropImage,
            onRotate = {
                val current = cropWorkingImage
                if (current != null) {
                    // Av samma skäl som decodeForCrop ovan: rotate90 kör en full rita+encode+
                    // avkoda-cykel (bakeUpright) — håll den av huvudtråden, precis som bygg-steget.
                    scope.launch {
                        cropWorkingImage = withContext(Dispatchers.Default) { rotate90(current) }
                    }
                }
            },
            onConfirm = { rect ->
                val toFinalize = cropWorkingImage ?: return@CropAdjustScreen
                cropWorkingImage = null
                viewModel.markAnalyzing()
                // På composable-scope, inte viewModelScope: backar användaren ut under det korta
                // finalize-fönstret avbryts crop→analys (samma avvägning som Android-hosten).
                scope.launch {
                    val input = withContext(Dispatchers.Default) { finalizeCrop(toFinalize, rect) }
                    if (input == null) {
                        viewModel.decodeFailed()
                        return@launch
                    }
                    viewModel.analyze(input)
                }
            },
            onCancel = cancelCrop,
        )
    } else {
        PhotoAnalyzeScreen(
            viewModel = viewModel,
            onPickFromGallery = {
                presentPhotoPicker(
                    onBytes = { bytes -> if (bytes != null) pendingBytes.value = bytes },
                    onPresentFailure = { viewModel.decodeFailed() },
                )
            },
            // i2b är galleri-only; ta-foto = i2c. No-op håller den delade skärmen orörd.
            onTakePhoto = {},
            onLoaded = onLoaded,
            onBack = onBack,
        )
    }
}
