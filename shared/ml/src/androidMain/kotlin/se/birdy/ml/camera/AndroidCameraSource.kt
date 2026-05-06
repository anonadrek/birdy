package se.birdy.ml.camera

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import se.birdy.ml.CameraSource
import se.birdy.ml.FrameFormat
import se.birdy.ml.ImageInput
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class AndroidCameraSource(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
) : CameraSource {
    private val executor = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null
    private val analysisFlow = MutableStateFlow<ImageAnalysis?>(null)
    private val lastJpeg = MutableStateFlow<ByteArray?>(null)
    val previewUseCase: Preview = Preview.Builder().build()

    fun lastJpegBytes(): ByteArray = lastJpeg.value ?: byteArrayOf()

    fun bindPreview(view: PreviewView) {
        previewUseCase.setSurfaceProvider(view.surfaceProvider)
    }

    override fun frames(): Flow<ImageInput> =
        callbackFlow {
            val analyzer =
                ImageAnalysis.Analyzer { proxy: ImageProxy ->
                    try {
                        val jpeg = proxy.toJpegBytes()
                        lastJpeg.value = jpeg
                        trySend(
                            ImageInput(
                                bytes = jpeg,
                                widthPx = proxy.width,
                                heightPx = proxy.height,
                                rotationDegrees = proxy.imageInfo.rotationDegrees,
                                format = FrameFormat.JPEG,
                                timestampMillis = System.currentTimeMillis(),
                            ),
                        )
                    } catch (t: Throwable) {
                        // per-frame errors dropped; ScanViewModel handles persistent failures
                    } finally {
                        proxy.close()
                    }
                }
            val analysis = analysisFlow.filterNotNull().first()
            analysis.setAnalyzer(executor, analyzer)
            awaitClose { analysis.clearAnalyzer() }
        }

    override suspend fun start() {
        val provider = awaitProvider()
        val analysis =
            ImageAnalysis
                .Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
        analysisFlow.value = analysis
        val selector = CameraSelector.DEFAULT_BACK_CAMERA
        provider.unbindAll()
        provider.bindToLifecycle(lifecycleOwner, selector, previewUseCase, analysis)
        cameraProvider = provider
    }

    override suspend fun stop() {
        cameraProvider?.unbindAll()
        analysisFlow.value?.clearAnalyzer()
        analysisFlow.value = null
    }

    private suspend fun awaitProvider(): ProcessCameraProvider =
        suspendCancellableCoroutine { cont ->
            val future: ListenableFuture<ProcessCameraProvider> =
                ProcessCameraProvider.getInstance(context)
            future.addListener(
                {
                    try {
                        cont.resume(future.get())
                    } catch (t: Throwable) {
                        cont.resumeWithException(t)
                    }
                },
                executor,
            )
        }
}
