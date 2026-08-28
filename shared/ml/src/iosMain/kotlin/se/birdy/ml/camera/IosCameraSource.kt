@file:OptIn(ExperimentalForeignApi::class)

package se.birdy.ml.camera

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.plus
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureDevicePositionBack
import platform.AVFoundation.AVCaptureDeviceTypeBuiltInWideAngleCamera
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionPreset1280x720
import platform.AVFoundation.AVCaptureVideoDataOutput
import platform.AVFoundation.AVCaptureVideoDataOutputSampleBufferDelegateProtocol
import platform.AVFoundation.AVCaptureVideoOrientationPortrait
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.defaultDeviceWithDeviceType
import platform.AVFoundation.videoZoomFactor
import platform.CoreMedia.CMSampleBufferGetImageBuffer
import platform.CoreMedia.CMSampleBufferRef
import platform.CoreMedia.CMTimeMake
import platform.CoreVideo.CVPixelBufferGetBaseAddress
import platform.CoreVideo.CVPixelBufferGetBytesPerRow
import platform.CoreVideo.CVPixelBufferGetHeight
import platform.CoreVideo.CVPixelBufferGetPixelFormatType
import platform.CoreVideo.CVPixelBufferGetWidth
import platform.CoreVideo.CVPixelBufferLockBaseAddress
import platform.CoreVideo.CVPixelBufferUnlockBaseAddress
import platform.CoreVideo.kCVPixelBufferLock_ReadOnly
import platform.CoreVideo.kCVPixelBufferPixelFormatTypeKey
import platform.CoreVideo.kCVPixelFormatType_32BGRA
import platform.Foundation.CFBridgingRelease
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970
import platform.darwin.NSObject
import platform.darwin.dispatch_queue_create
import platform.posix.memcpy
import se.birdy.ml.CameraSource
import se.birdy.ml.FrameFormat
import se.birdy.ml.ImageInput
import se.birdy.ml.ZoomState
import kotlin.concurrent.Volatile

/**
 * AVFoundation-implementation av [CameraSource] (i2c) — spegel av [AndroidCameraSource]:
 * bakre vidvinkelkamera, BGRA-frames (AVCaptures native-format, se FrameFormat.BGRA_8888-
 * grenen i ImagePreprocessor.ios), portrait-låsta connections (rotationDegrees = 0),
 * wall-clock-timestamps (freshness-guarden i ScanViewModel jämför mot klockan — trap-
 * katalogen). `alwaysDiscardsLateVideoFrames` dumpar sena frames innan de når delegaten,
 * men det räcker inte ensamt — [frames] är `.conflate()`:ad så bara senaste framen hålls
 * kvar när konsumenten (klassificeringen på huvudtråden) släpar efter; tillsammans
 * motsvarar de två CameraX STRATEGY_KEEP_ONLY_LATEST.
 *
 * Preset 1280x720 + ~15 fps-cap: presetten styr BÅDE preview och data-output på iOS
 * (till skillnad från CameraX:s oberoende use cases) — 720p ger skarp preview och
 * måttliga buffertar; ScanViewModel samplar ändå ner till 3/1.5 fps.
 *
 * Simulator/kameralös enhet: start() hittar ingen device → loggar, lämnar ZoomState.NONE,
 * inga frames — skärmen visar svart preview + "searching…" (medveten tyst modell, spec §5).
 *
 * start()/stop() racar likadant som Android: ScanViewModel start:ar på viewModelScope
 * och stop:ar från GlobalScope i onCleared. `startRunning()` är blockerande och inte
 * ett cancellation-point — utan en terminal [stopped]-flagga kan stop() se sessionen
 * som inte-running och returnera, varefter start() tänder kameran mot en redan
 * lämnad Scan-skärm (grön statusbar-prick kvar). [stopped] sätts FÖRE mutex så en
 * in-flight startRunning stannar sig själv.
 */
class IosCameraSource : CameraSource {
    // Skapas eagert så CameraPreviewHost kan koppla sin AVCaptureVideoPreviewLayer innan
    // start() hunnit konfigurera in-/utgångar (motsvarar AndroidCameraSource.previewUseCase).
    val captureSession = AVCaptureSession()

    // Läses från UI-tråden (setZoomRatio) men skrivs i start()/stop() på Dispatchers.Default
    // → @Volatile för synlighet (set-once-on-start / null-on-stop), spegel av
    // AndroidCameraSource.camera.
    @Volatile
    private var device: AVCaptureDevice? = null
    private var configured = false
    private val outputFlow = MutableStateFlow<AVCaptureVideoDataOutput?>(null)
    private val _zoom = MutableStateFlow(ZoomState.NONE)
    override val zoom: StateFlow<ZoomState> = _zoom.asStateFlow()
    private val lifecycleMutex = Mutex()

    @Volatile
    private var stopped = false

    private val frameQueue = dispatch_queue_create("se.birdy.camera.frames", null)

    // setSampleBufferDelegate lovar inte att retaina delegaten — håll den starkt själv
    // (samma buggklass som weak PHPicker.delegate, i2b).
    private var frameDelegate: FrameDelegate? = null

    // callbackFlow:s default 64-slot buffert kan annars hålla kvar ~236 MB (64 × ~3.7 MB)
    // rådata om huvudtråden släpar under klassificering — conflate() nedan = bara senaste
    // framen, vilket är det som faktiskt ger KEEP_ONLY_LATEST-paritet Kotlin-sidan.
    override fun frames(): Flow<ImageInput> =
        callbackFlow {
            val output = outputFlow.filterNotNull().first()
            val delegate = FrameDelegate { input -> trySend(input) }
            frameDelegate = delegate
            output.setSampleBufferDelegate(delegate, frameQueue)
            awaitClose {
                output.setSampleBufferDelegate(null, null)
                frameDelegate = null
            }
        }.conflate()

    override suspend fun start(): Unit =
        withContext(Dispatchers.Default) {
            if (stopped) return@withContext
            lifecycleMutex.withLock {
                if (stopped) return@withLock
                val cam =
                    AVCaptureDevice.defaultDeviceWithDeviceType(
                        deviceType = AVCaptureDeviceTypeBuiltInWideAngleCamera,
                        mediaType = AVMediaTypeVideo,
                        position = AVCaptureDevicePositionBack,
                    )
                if (cam == null) {
                    println("IosCameraSource: no back wide-angle camera (simulator?) — no frames will flow")
                    return@withLock
                }
                if (!configured && !configureSession(cam)) return@withLock
                device = cam
                // startRunning blockerar → körs på Default-dispatchern, aldrig main.
                captureSession.startRunning()
                if (stopped) {
                    if (captureSession.isRunning()) captureSession.stopRunning()
                    device = null
                    _zoom.value = ZoomState.NONE
                    return@withLock
                }
                val max = cam.activeFormat.videoMaxZoomFactor.toFloat()
                _zoom.value = ZoomState(ratio = 1f, minRatio = 1f, maxRatio = max)
                if (cam.lockForConfiguration(null)) {
                    cam.videoZoomFactor = 1.0
                    cam.unlockForConfiguration()
                }
            }
        }

    private fun configureSession(cam: AVCaptureDevice): Boolean {
        val input = AVCaptureDeviceInput.deviceInputWithDevice(cam, null)
        if (input == null || !captureSession.canAddInput(input)) {
            println("IosCameraSource: cannot add camera input")
            return false
        }
        captureSession.beginConfiguration()
        captureSession.sessionPreset = AVCaptureSessionPreset1280x720
        captureSession.addInput(input)
        val output = AVCaptureVideoDataOutput()
        // Default-formatet är biplanärt YUV — BGRA måste begäras explicit. CFBridgingRelease
        // på den odödliga CF-konstanten bryggar nyckeln till NSString för Kotlin-mappen.
        output.videoSettings =
            mapOf(CFBridgingRelease(kCVPixelBufferPixelFormatTypeKey) to kCVPixelFormatType_32BGRA)
        output.alwaysDiscardsLateVideoFrames = true
        if (!captureSession.canAddOutput(output)) {
            captureSession.commitConfiguration()
            println("IosCameraSource: cannot add video data output")
            return false
        }
        captureSession.addOutput(output)
        // Portrait-lås: frames anländer upprätta → rotationDegrees = 0 (spec §5).
        (output.connectionWithMediaType(AVMediaTypeVideo) as? AVCaptureConnection)?.let { conn ->
            if (conn.isVideoOrientationSupported()) {
                conn.videoOrientation = AVCaptureVideoOrientationPortrait
            }
        }
        captureSession.commitConfiguration()
        // Cap ~15 fps: trimmar spillkopior mellan VM:ens 333/666 ms-samplen.
        if (cam.lockForConfiguration(null)) {
            cam.activeVideoMinFrameDuration = CMTimeMake(value = 1, timescale = 15)
            cam.unlockForConfiguration()
        }
        outputFlow.value = output
        configured = true
        return true
    }

    override suspend fun stop(): Unit =
        withContext(Dispatchers.Default) {
            stopped = true
            lifecycleMutex.withLock {
                if (captureSession.isRunning()) captureSession.stopRunning()
                device = null
                _zoom.value = ZoomState.NONE
            }
        }

    override fun setZoomRatio(ratio: Float) {
        val current = _zoom.value
        val clamped = ratio.coerceIn(current.minRatio, current.maxRatio)
        device?.let { cam ->
            if (cam.lockForConfiguration(null)) {
                cam.videoZoomFactor = clamped.toDouble()
                cam.unlockForConfiguration()
            }
        }
        _zoom.value = current.copy(ratio = clamped)
    }

    private class FrameDelegate(
        private val onFrame: (ImageInput) -> Unit,
    ) : NSObject(),
        AVCaptureVideoDataOutputSampleBufferDelegateProtocol {
        override fun captureOutput(
            output: AVCaptureOutput,
            didOutputSampleBuffer: CMSampleBufferRef?,
            fromConnection: AVCaptureConnection,
        ) {
            val pixelBuffer = didOutputSampleBuffer?.let { CMSampleBufferGetImageBuffer(it) } ?: return
            // Defensiv format-guard: en icke-BGRA-buffert får ALDRIG in i pipelinen
            // (tyst kanalbyte = fel art med rimlig konfidens).
            if (CVPixelBufferGetPixelFormatType(pixelBuffer) != kCVPixelFormatType_32BGRA) return
            try {
                CVPixelBufferLockBaseAddress(pixelBuffer, kCVPixelBufferLock_ReadOnly)
                try {
                    val base = CVPixelBufferGetBaseAddress(pixelBuffer)?.reinterpret<ByteVar>() ?: return
                    val width = CVPixelBufferGetWidth(pixelBuffer).toInt()
                    val height = CVPixelBufferGetHeight(pixelBuffer).toInt()
                    val bytesPerRow = CVPixelBufferGetBytesPerRow(pixelBuffer).toInt()
                    val bytes = copyCompactedBgra(base, bytesPerRow, width, height)
                    onFrame(
                        ImageInput(
                            bytes = bytes,
                            widthPx = width,
                            heightPx = height,
                            rotationDegrees = 0,
                            format = FrameFormat.BGRA_8888,
                            // Wall clock, INTE sensortid — freshness-guarden jämför mot klockan.
                            timestampMillis = (NSDate().timeIntervalSince1970 * 1000).toLong(),
                        ),
                    )
                } finally {
                    CVPixelBufferUnlockBaseAddress(pixelBuffer, kCVPixelBufferLock_ReadOnly)
                }
            } catch (t: Throwable) {
                // per-frame errors dropped; ScanViewModel handles persistent failures
            }
        }
    }
}

/**
 * Packar [height] rader BGRA från en CVPixelBuffer-bas till exakt `width*height*4` bytes.
 * bytesPerRow kan överstiga width*4 (alignment-padding) — preprocessorns storlekskrav
 * tillåter inte padding.
 */
internal fun copyCompactedBgra(
    base: CPointer<ByteVar>,
    bytesPerRow: Int,
    width: Int,
    height: Int,
): ByteArray {
    val rowBytes = width * 4
    require(bytesPerRow >= rowBytes) { "bytesPerRow $bytesPerRow < packed row $rowBytes" }
    val out = ByteArray(rowBytes * height)
    out.usePinned { dst ->
        if (bytesPerRow == rowBytes) {
            memcpy(dst.addressOf(0), base, (rowBytes * height).convert())
        } else {
            for (r in 0 until height) {
                memcpy(dst.addressOf(r * rowBytes), base + (r * bytesPerRow), rowBytes.convert())
            }
        }
    }
    return out
}
