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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * CameraX capture source. Analysis + provider-future callbacks run on [executor]
 * (default: a dedicated single-thread pool).
 *
 * [stop] is terminal: it shuts the pool down. Scan is a pushed nav destination, so each
 * visit constructs a new [AndroidCameraSource]; leaving Scan calls [stop] from the
 * ViewModel's `onCleared`. Leaving the pool alive leaked a ~1 MB stack thread per
 * scan session (same class of leak as the geotag executor in AndroidLocationProvider
 * — a ThreadPoolExecutor core thread never times out).
 *
 * [start] and [stop] race: `ScanViewModel` launches start() on viewModelScope, then
 * onCleared dispatches stop() on GlobalScope. `awaitProvider` resumes on [executor],
 * so `bindToLifecycle` also runs there — and it is not a cancellation point. Without
 * a terminal [stopped] flag, stop() can observe a still-null [cameraProvider] and
 * return, after which start() finishes the bind against the Activity lifecycle.
 * Result: camera LED stays on after leaving Scan. [stopped] is set *before* taking
 * [lifecycleLock] so an in-flight bind unbinds itself; stop() then unbinds again.
 */
class AndroidCameraSource(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val executor: ExecutorService = Executors.newSingleThreadExecutor(),
) : CameraSource {
    private var cameraProvider: ProcessCameraProvider? = null
    private val lifecycleLock = Any()

    @Volatile
    private var stopped = false

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
        if (stopped) return
        val provider = awaitProvider()
        synchronized(lifecycleLock) {
            if (stopped) return
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
            if (stopped) {
                // stop() flipped the flag while we held the lock (it sets [stopped]
                // before waiting). Don't leave the Activity-scoped camera bound.
                unbindLocked()
            }
        }
    }

    override suspend fun stop() {
        stopped = true
        synchronized(lifecycleLock) {
            unbindLocked()
            // After clearAnalyzer so an in-flight frame can finish on this pool first.
            // shutdown() (not shutdownNow): already-submitted analysis should complete.
            // Idempotent — onCleared can theoretically race a second stop.
            executor.shutdown()
        }
    }

    private fun unbindLocked() {
        cameraProvider?.unbindAll()
        analysisFlow.value?.clearAnalyzer()
        analysisFlow.value = null
        camera = null
        cameraProvider = null
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
