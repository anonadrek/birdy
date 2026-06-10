package se.birdy.ml.camera

import android.content.Context
import androidx.camera.core.Camera
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import se.birdy.ml.CameraSource
import se.birdy.ml.FrameFormat
import se.birdy.ml.ImageInput
import se.birdy.ml.ZoomState
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class AndroidCameraSource(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
) : CameraSource {
    private val executor = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null

    // Läses från UI-tråden (setZoomRatio) men skrivs i start()/stop() på coroutine-/
    // kamera-executor-trådar → @Volatile för synlighet (set-once-on-bind / null-on-stop).
    @Volatile
    private var camera: Camera? = null
    private val _zoom = MutableStateFlow(ZoomState.NONE)
    override val zoom: StateFlow<ZoomState> = _zoom.asStateFlow()
    private val analysisFlow = MutableStateFlow<ImageAnalysis?>(null)
    val previewUseCase: Preview = Preview.Builder().build()

    fun bindPreview(view: PreviewView) {
        previewUseCase.setSurfaceProvider(view.surfaceProvider)
    }

    override fun frames(): Flow<ImageInput> =
        callbackFlow {
            val analyzer =
                ImageAnalysis.Analyzer { proxy: ImageProxy ->
                    try {
                        val jpeg = proxy.toJpegBytes()
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
        val boundCamera =
            provider.bindToLifecycle(lifecycleOwner, selector, previewUseCase, analysis)
        camera = boundCamera
        val max =
            boundCamera.cameraInfo.zoomState.value
                ?.maxZoomRatio ?: 1f
        _zoom.value = ZoomState(ratio = 1f, minRatio = 1f, maxRatio = max)
        boundCamera.cameraControl.setZoomRatio(1f)
        cameraProvider = provider
    }

    override suspend fun stop() {
        cameraProvider?.unbindAll()
        analysisFlow.value?.clearAnalyzer()
        analysisFlow.value = null
        camera = null
        _zoom.value = ZoomState.NONE
    }

    override fun setZoomRatio(ratio: Float) {
        val current = _zoom.value
        val clamped = ratio.coerceIn(current.minRatio, current.maxRatio)
        camera?.cameraControl?.setZoomRatio(clamped)
        _zoom.value = current.copy(ratio = clamped)
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
