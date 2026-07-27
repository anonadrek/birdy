# iOS i2c — Live camera + take-photo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Real live-camera scan (AVCaptureSession, BGRA end-to-end, zoom) + working take-photo button on iOS, wired into the untouched shared scan/match pipeline — the code for Milestone 1.

**Architecture:** A new `IosCameraSource` (shared/ml iosMain) emits portrait-locked BGRA frames into the existing platform-agnostic `ScanViewModel`; a new `FrameFormat.BGRA_8888` branch in the iOS preprocessor makes the channel-swap trap impossible by construction; JPEG encoding happens only at freeze via a new `persistFrame(ImageInput)` seam (the plan's only shared-code change). Hosts mirror their Android counterparts file-for-file. Spec: `docs/superpowers/specs/2026-07-26-ios-i2c-live-camera-design.md`.

**Tech Stack:** Kotlin/Native + AVFoundation/CoreVideo/CoreMedia interop (no CocoaPods, no new Swift), Compose Multiplatform 1.8.2 `UIKitView`, existing TensorFlowLiteC runtime from i2b.

## Global Constraints

- **Android stays shippable after every commit:** `:shared:domain:jvmTest :shared:ml:jvmTest :composeApp:testDebugUnitTest :androidApp:assembleDebug` green, plus `ktlintCheck detekt`.
- Every Gradle command runs from the repo root on the Mac. If Gradle complains about Java: `export JAVA_HOME="$HOME/.local/java21/Contents/Home"` first.
- min iOS 16.0; bundle `se.birdy.ios`; `iosApp/project.yml` is the xcodegen source of truth (`cd iosApp && xcodegen generate` after changing it or adding files under `iosApp/iosApp/`).
- No new dependencies. No CocoaPods. Swift layer untouched.
- `ImageInput.timestampMillis` MUST be wall clock (`NSDate().timeIntervalSince1970`) — sensor time makes every freeze read stale (trap catalog).
- Comments in new iOS files follow the existing style (Swedish or English matching the file's neighbors; both exist in iosMain).
- Do NOT run `xcodebuild`/simulator steps in parallel with Gradle steps (shared Kotlin/Native compile locks).
- Device verification is an OPEN GATE (no iPhone available) — the plan ends at "code complete + sim-verified", not "Milestone 1 closed". Do not claim Milestone 1 in commits or docs.

---

### Task 1: `FrameFormat.BGRA_8888` + preprocessor branches + parity guards

**Files:**
- Modify: `shared/ml/src/commonMain/kotlin/se/birdy/ml/BirdClassifier.kt:3` (enum)
- Modify: `shared/ml/src/iosMain/kotlin/se/birdy/ml/ImagePreprocessor.ios.kt` (BGRA branch)
- Modify: `shared/ml/src/androidMain/kotlin/se/birdy/ml/ImagePreprocessor.android.kt:48-55` (exhaustive `when` gains an error branch)
- Create: `shared/ml/src/iosTest/kotlin/se/birdy/ml/ImagePreprocessorBgraTest.kt`
- Create: `shared/ml/src/iosTest/kotlin/se/birdy/ml/IosBgraClassifierParityTest.kt`

**Interfaces:**
- Consumes: existing `ImageInput`, `ImagePreprocessor`, `IosTfliteRunner`, `TfLiteBirdClassifier`, parity fixture `files/testdata/parity_Q180991.jpg` (commonMain composeResources).
- Produces: `FrameFormat.BGRA_8888` enum value; `ImagePreprocessor` (iOS) accepts `ImageInput(format = BGRA_8888)` with bytes laid out B,G,R,A row-major, size exactly `w*h*4`. Later tasks (2, 4) rely on exactly this contract.

- [ ] **Step 1: Write the failing preprocessor test**

Create `shared/ml/src/iosTest/kotlin/se/birdy/ml/ImagePreprocessorBgraTest.kt`. It mirrors `ImagePreprocessorIosTest` but feeds raw BGRA bytes (no PNG needed — the raw-format path takes bytes directly). Asymmetric channel values catch a B/R swap:

```kotlin
package se.birdy.ml

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the BGRA_8888 → RGB float contract for the iOS [ImagePreprocessor] (i2c).
 * BGRA is AVCaptureVideoDataOutput's native layout; feeding it through the RGBA path
 * produces silently channel-swapped inference — this test makes that regression loud.
 * Same 2×2 primaries fixture as [ImagePreprocessorIosTest], but as raw BGRA bytes.
 */
class ImagePreprocessorBgraTest {
    // Row-major BGRA: TL red, TR green, BL blue, BR white.
    private val bgraBytes: ByteArray =
        ubyteArrayOf(
            0x00u, 0x00u, 0xFFu, 0xFFu, // (0,0) red   = B0 G0 R255 A255
            0x00u, 0xFFu, 0x00u, 0xFFu, // (0,1) green
            0xFFu, 0x00u, 0x00u, 0xFFu, // (1,0) blue
            0xFFu, 0xFFu, 0xFFu, 0xFFu, // (1,1) white
        ).toByteArray()

    private val identityMean = floatArrayOf(0.5f, 0.5f, 0.5f)
    private val identityStd = floatArrayOf(0.5f, 0.5f, 0.5f)
    private val eps = 1e-3f

    private fun n(v: Int): Float = (v / 255f - 0.5f) / 0.5f

    @Test
    fun preprocess_bgra_2x2_matches_rgba_reference_floats() {
        val input =
            ImageInput(
                bytes = bgraBytes,
                widthPx = 2,
                heightPx = 2,
                rotationDegrees = 0,
                format = FrameFormat.BGRA_8888,
                timestampMillis = 0L,
            )

        val out =
            ImagePreprocessor().preprocess(
                input,
                outHeight = 2,
                outWidth = 2,
                normalizationMean = identityMean,
                normalizationStd = identityStd,
            )

        assertEquals(2 * 2 * 3, out.size, "must be outH*outW*3 floats")
        // RGB row-major, top-left first: red, green, blue, white — identical to the RGBA/JPEG
        // reference in ImagePreprocessorIosTest. A B/R swap turns red into blue and fails loudly.
        val expected =
            floatArrayOf(
                n(255), n(0), n(0),
                n(0), n(255), n(0),
                n(0), n(0), n(255),
                n(255), n(255), n(255),
            )
        for (i in expected.indices) {
            assertEquals(expected[i], out[i], eps, "float[$i] mismatch")
        }
    }
}
```

- [ ] **Step 2: Run it to verify it fails (BGRA_8888 does not exist yet → compile error)**

Run: `./gradlew :shared:ml:compileKotlinIosSimulatorArm64 --stacktrace 2>&1 | tail -20`
Expected: FAIL — `Unresolved reference 'BGRA_8888'`.

- [ ] **Step 3: Add the enum value + both preprocessor branches**

In `shared/ml/src/commonMain/kotlin/se/birdy/ml/BirdClassifier.kt` line 3:

```kotlin
enum class FrameFormat { YUV_420_888, JPEG, RGBA_8888, BGRA_8888 }
```

In `shared/ml/src/iosMain/kotlin/se/birdy/ml/ImagePreprocessor.ios.kt`, extend the format `when` (currently lines 66-73) and update the YUV stub text:

```kotlin
            val native =
                when (input.format) {
                    FrameFormat.RGBA_8888 -> decodeRgba(input)
                    FrameFormat.BGRA_8888 -> decodeBgra(input)
                    FrameFormat.JPEG -> decodeCompressed(input.bytes, colorSpace)
                    FrameFormat.YUV_420_888 ->
                        throw NotImplementedError(
                            "YUV_420_888 preprocessing is Android-only; iOS camera frames are BGRA_8888 (i2c).",
                        )
                }
```

Add `decodeBgra` next to `decodeRgba` (after line 100):

```kotlin
    private fun decodeBgra(input: ImageInput): Rgba {
        val expected = input.widthPx * input.heightPx * 4
        require(input.bytes.size == expected) {
            "BGRA_8888 expects ${expected}B for ${input.widthPx}x${input.heightPx}, got ${input.bytes.size}"
        }
        // Fused swizzle: the RGBA path already pays a defensive copy here (decodeRgba's
        // copyOf); reordering B↔R inside that same copy is the zero-extra-cost format
        // bridge — no separate per-frame conversion pass ever runs.
        val src = input.bytes.asUByteArray()
        val out = UByteArray(expected)
        var i = 0
        while (i < expected) {
            out[i] = src[i + 2]
            out[i + 1] = src[i + 1]
            out[i + 2] = src[i]
            out[i + 3] = src[i + 3]
            i += 4
        }
        return Rgba(out, input.widthPx, input.heightPx)
    }
```

In `shared/ml/src/androidMain/kotlin/se/birdy/ml/ImagePreprocessor.android.kt`, the `when (input.format)` at line 49 is exhaustive — add:

```kotlin
            FrameFormat.BGRA_8888 ->
                error("BGRA_8888 frames are iOS-only; Android sources emit JPEG/YUV/RGBA")
```

- [ ] **Step 4: Run the preprocessor test to verify it passes**

Run: `./gradlew :shared:ml:iosSimulatorArm64Test --tests "se.birdy.ml.ImagePreprocessorBgraTest" --stacktrace`
Expected: PASS.

- [ ] **Step 5: Write the BGRA whole-classifier parity test**

Create `shared/ml/src/iosTest/kotlin/se/birdy/ml/IosBgraClassifierParityTest.kt`. It reuses the committed fixture without any new decode hook: preprocessing the JPEG with identity normalization (`mean=0, std=1/255`) yields floats equal to the raw pixel bytes, which we repack as BGRA:

```kotlin
package se.birdy.ml

import birdy_bird_scanner.shared.ml.generated.resources.Res
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.resources.ExperimentalResourceApi
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * BGRA parity guard (i2c): proves the [FrameFormat.BGRA_8888] branch feeds the REAL
 * classifier the same pixels as the JPEG/RGBA reference path. Reuses the i2b parity
 * fixture: decode it to raw pixel values via the JPEG path with identity normalization
 * (mean=0, std=1/255 → out == pixel byte values), repack as BGRA, classify. A channel
 * swap in the BGRA branch would misclassify or crater the confidence — mechanically
 * impossible to reintroduce unnoticed (this runs in CI's macOS job).
 */
@OptIn(ExperimentalForeignApi::class, ExperimentalResourceApi::class)
class IosBgraClassifierParityTest {
    @Test
    fun classifies_bgra_repacked_fixture_with_reference_species_and_confidence() =
        runTest {
            val fixtureBytes = Res.readBytes("files/testdata/parity_Q180991.jpg")
            val pixels =
                ImagePreprocessor().preprocess(
                    ImageInput(fixtureBytes, widthPx = 512, heightPx = 341, format = FrameFormat.JPEG),
                    outHeight = 341,
                    outWidth = 512,
                    normalizationMean = floatArrayOf(0f, 0f, 0f),
                    normalizationStd = floatArrayOf(1 / 255f, 1 / 255f, 1 / 255f),
                )
            val bgra = ByteArray(512 * 341 * 4)
            var p = 0
            for (i in 0 until 512 * 341) {
                val r = pixels[p++].roundToInt()
                val g = pixels[p++].roundToInt()
                val b = pixels[p++].roundToInt()
                bgra[i * 4] = b.toByte()
                bgra[i * 4 + 1] = g.toByte()
                bgra[i * 4 + 2] = r.toByte()
                bgra[i * 4 + 3] = 0xFF.toByte()
            }
            val image = ImageInput(bgra, widthPx = 512, heightPx = 341, format = FrameFormat.BGRA_8888)

            val info = loadModelMetadata()
            val modelBytes = ModelArtifactProvider().loadModelBytes(info)
            val mapper = loadAiyLabelMapper()
            val mean = info.normalizationMean.toFloatArray()
            val std = info.normalizationStd.toFloatArray()
            val runner = IosTfliteRunner(modelBytes, info)
            val classification =
                try {
                    TfLiteBirdClassifier(
                        info = info,
                        runner = runner,
                        preprocess = { img, mi ->
                            ImagePreprocessor().preprocess(img, mi.inputHeightPx, mi.inputWidthPx, mean, std)
                        },
                        mapper = mapper,
                    ).classify(image)
                } finally {
                    runner.close()
                }

            val top = classification.results.firstOrNull()
            assertNotNull(top, "classification produced no results above threshold")
            assertEquals("Q180991", top.speciesId, "top-1 species mismatch (got ${top.speciesId} @ ${top.confidence})")
            val delta = abs(top.confidence - REFERENCE_CONFIDENCE)
            assertTrue(delta <= TOLERANCE, "confidence ${top.confidence} off reference by $delta (> $TOLERANCE)")
        }

    private companion object {
        // Same desktop ai-edge-litert reference as IosClassifierParityTest.
        const val REFERENCE_CONFIDENCE = 0.9531f
        const val TOLERANCE = 0.05f
    }
}
```

- [ ] **Step 6: Run both new tests + the full shared/ml iOS suite**

Run: `./gradlew :shared:ml:iosSimulatorArm64Test --stacktrace`
Expected: PASS (new tests + pre-existing ImagePreprocessorIosTest/IosClassifierParityTest/IosTfliteSmokeTest all green).

- [ ] **Step 7: Android regression (enum touched commonMain + androidMain)**

Run: `./gradlew :shared:ml:jvmTest :shared:domain:jvmTest :composeApp:testDebugUnitTest :androidApp:assembleDebug ktlintCheck detekt --stacktrace`
Expected: all green.

- [ ] **Step 8: Commit**

```bash
git add shared/ml
git commit -m "feat(ml): FrameFormat.BGRA_8888 + iOS preprocessor branch with parity guards (i2c T1)"
```

---

### Task 2: `IosCameraSource` — AVCaptureSession frame pump + zoom

**Files:**
- Create: `shared/ml/src/iosMain/kotlin/se/birdy/ml/camera/IosCameraSource.kt`
- Create: `shared/ml/src/iosTest/kotlin/se/birdy/ml/camera/CopyCompactedBgraTest.kt`

**Interfaces:**
- Consumes: `CameraSource`, `ImageInput`, `FrameFormat.BGRA_8888` (Task 1), `ZoomState`.
- Produces: `class IosCameraSource : CameraSource` with `val captureSession: AVCaptureSession` (eager, for the preview layer — Task 5 depends on this exact property name), `frames(): Flow<ImageInput>` (BGRA_8888, portrait-upright, wall-clock timestamps), `zoom`/`setZoomRatio` per the shared contract, and `internal fun copyCompactedBgra(base: CPointer<ByteVar>, bytesPerRow: Int, width: Int, height: Int): ByteArray`.

- [ ] **Step 1: Write the failing stride-compaction test**

Create `shared/ml/src/iosTest/kotlin/se/birdy/ml/camera/CopyCompactedBgraTest.kt`:

```kotlin
package se.birdy.ml.camera

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * CVPixelBuffer rows can be padded (bytesPerRow > width*4) for alignment; the
 * preprocessor requires exactly w*h*4 bytes. This pins the row-compaction contract —
 * i2c's sneaky-detail equivalent of i2b's lifetime-pin.
 */
@OptIn(ExperimentalForeignApi::class)
class CopyCompactedBgraTest {
    @Test
    fun compacts_padded_rows_to_exactly_w_h_4() {
        val width = 2
        val height = 2
        val bytesPerRow = 12 // 8 payload + 4 pad per row
        val src = ByteArray(bytesPerRow * height) { it.toByte() }
        val out =
            src.usePinned { pinned ->
                copyCompactedBgra(pinned.addressOf(0), bytesPerRow, width, height)
            }
        assertEquals(width * height * 4, out.size)
        assertContentEquals(src.copyOfRange(0, 8) + src.copyOfRange(12, 20), out)
    }

    @Test
    fun tight_rows_copy_through_unchanged() {
        val width = 3
        val height = 2
        val bytesPerRow = width * 4
        val src = ByteArray(bytesPerRow * height) { (it * 7).toByte() }
        val out =
            src.usePinned { pinned ->
                copyCompactedBgra(pinned.addressOf(0), bytesPerRow, width, height)
            }
        assertContentEquals(src, out)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :shared:ml:compileTestKotlinIosSimulatorArm64 --stacktrace 2>&1 | tail -20`
Expected: FAIL — `Unresolved reference 'copyCompactedBgra'` (package `se.birdy.ml.camera` does not exist in iosMain yet).

- [ ] **Step 3: Implement `IosCameraSource`**

Create `shared/ml/src/iosMain/kotlin/se/birdy/ml/camera/IosCameraSource.kt`:

```kotlin
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
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
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

/**
 * AVFoundation-implementation av [CameraSource] (i2c) — spegel av [AndroidCameraSource]:
 * bakre vidvinkelkamera, BGRA-frames (AVCaptures native-format, se FrameFormat.BGRA_8888-
 * grenen i ImagePreprocessor.ios), portrait-låsta connections (rotationDegrees = 0),
 * wall-clock-timestamps (freshness-guarden i ScanViewModel jämför mot klockan — trap-
 * katalogen), och `alwaysDiscardsLateVideoFrames` = CameraX KEEP_ONLY_LATEST.
 *
 * Preset 1280x720 + ~15 fps-cap: presetten styr BÅDE preview och data-output på iOS
 * (till skillnad från CameraX:s oberoende use cases) — 720p ger skarp preview och
 * måttliga buffertar; ScanViewModel samplar ändå ner till 3/1.5 fps.
 *
 * Simulator/kameralös enhet: start() hittar ingen device → loggar, lämnar ZoomState.NONE,
 * inga frames — skärmen visar svart preview + "searching…" (medveten tyst modell, spec §5).
 */
class IosCameraSource : CameraSource {
    // Skapas eagert så CameraPreviewHost kan koppla sin AVCaptureVideoPreviewLayer innan
    // start() hunnit konfigurera in-/utgångar (motsvarar AndroidCameraSource.previewUseCase).
    val captureSession = AVCaptureSession()

    private var device: AVCaptureDevice? = null
    private var configured = false
    private val outputFlow = MutableStateFlow<AVCaptureVideoDataOutput?>(null)
    private val _zoom = MutableStateFlow(ZoomState.NONE)
    override val zoom: StateFlow<ZoomState> = _zoom.asStateFlow()

    private val frameQueue = dispatch_queue_create("se.birdy.camera.frames", null)

    // setSampleBufferDelegate lovar inte att retaina delegaten — håll den starkt själv
    // (samma buggklass som weak PHPicker.delegate, i2b).
    private var frameDelegate: FrameDelegate? = null

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
        }

    override suspend fun start(): Unit =
        withContext(Dispatchers.Default) {
            val cam =
                AVCaptureDevice.defaultDeviceWithDeviceType(
                    deviceType = AVCaptureDeviceTypeBuiltInWideAngleCamera,
                    mediaType = AVMediaTypeVideo,
                    position = AVCaptureDevicePositionBack,
                )
            if (cam == null) {
                println("IosCameraSource: no back wide-angle camera (simulator?) — no frames will flow")
                return@withContext
            }
            if (!configured && !configureSession(cam)) return@withContext
            device = cam
            // startRunning blockerar → körs på Default-dispatchern, aldrig main.
            captureSession.startRunning()
            val max = cam.activeFormat.videoMaxZoomFactor.toFloat()
            _zoom.value = ZoomState(ratio = 1f, minRatio = 1f, maxRatio = max)
            if (cam.lockForConfiguration(null)) {
                cam.videoZoomFactor = 1.0
                cam.unlockForConfiguration()
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
            if (captureSession.isRunning()) captureSession.stopRunning()
            device = null
            _zoom.value = ZoomState.NONE
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
```

Interop notes for the implementer (verify against the compiler, not from memory):
- `AVCaptureSession.running` bridges as the **function** `isRunning()` (same class of bridge as `isKeyWindow()`, trap catalog).
- `lockForConfiguration(null)` returns `Boolean`; the parameter is an error out-pointer.
- If `CFBridgingRelease` fails to resolve from `platform.Foundation`, it lives in `platform.CoreFoundation` on some SDK versions — try both imports.
- If `plus` on `CPointer<ByteVar>` does not resolve, replace `base + (r * bytesPerRow)` with `base.plus(r * bytesPerRow)` or interop-offset via `interpretCPointer`; the arithmetic import is `kotlinx.cinterop.plus`.
- If `videoOrientation` triggers a deprecation **warning** (iOS 17 RotationCoordinator), that is expected and accepted — min iOS is 16, the API works, and warnings are not errors. Do not switch API.

- [ ] **Step 4: Run the stride tests**

Run: `./gradlew :shared:ml:iosSimulatorArm64Test --tests "se.birdy.ml.camera.CopyCompactedBgraTest" --stacktrace`
Expected: PASS.

- [ ] **Step 5: Full shared/ml gates (both platforms)**

Run: `./gradlew :shared:ml:iosSimulatorArm64Test :shared:ml:jvmTest :shared:ml:compileKotlinIosArm64 ktlintCheck detekt --stacktrace`
Expected: all green (`compileKotlinIosArm64` proves the device target links the AVFoundation symbols too).

- [ ] **Step 6: Commit**

```bash
git add shared/ml
git commit -m "feat(ml): IosCameraSource — AVCaptureSession BGRA frame pump + zoom (i2c T2)"
```

---

### Task 3: Shared `persistFrame` signature — `(ByteArray) -> String` → `(ImageInput) -> String`

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scan/ScanViewModel.kt:148-151`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scan/ScanScreen.kt:99` (+ import)
- Modify: `composeApp/src/androidMain/kotlin/se/birdy/app/ui/scan/ScanScreenHost.android.kt:44-48`
- Modify: `composeApp/src/commonTest/kotlin/se/birdy/app/ui/scan/ScanViewModelTest.kt` (new test)

**Interfaces:**
- Consumes: `ImageInput` (commonMain).
- Produces: `ScanViewModel.onFreeze(persist: (ImageInput) -> String)` and `ScanScreen(persistFrame: (ImageInput) -> String)`. Task 5's iOS host implements this exact lambda type. The Android host keeps writing `input.bytes` verbatim (its frames are already JPEG).

- [ ] **Step 1: Write the failing test**

Add to `ScanViewModelTest.kt` (existing freeze lambdas use `{ _ -> ... }` and compile unchanged; this new test pins the richer contract):

```kotlin
    @Test
    fun freeze_passes_the_paired_frame_to_persist() =
        runTest(dispatcher) {
            val cameraSource = FakeCameraSource()
            val vm =
                ScanViewModel(
                    classifier = FakeBirdClassifier(),
                    cameraSourceFactory = { cameraSource },
                    frameThrottling = false,
                    nowMillis = { 142L },
                )
            vm.onPermissionResult(granted = true)
            vm.state.test {
                assertEquals(ScanUiState.Idle, awaitItem())
                cameraSource.emit(timestampMillis = 42L)
                assertIs<ScanUiState.Scanning>(awaitItem())

                var received: ImageInput? = null
                vm.onFreeze { input ->
                    received = input
                    "/cache/scan-frames/pair.jpg"
                }
                assertIs<ScanUiState.FrozenAt>(awaitItem())
                // persist must receive the FULL paired frame (format/dims/timestamp), not
                // just bytes — the iOS host JPEG-encodes BGRA frames and needs all of it.
                assertEquals(42L, received?.timestampMillis)
                assertEquals(224, received?.widthPx)
                assertEquals(FrameFormat.JPEG, received?.format)
                cancelAndIgnoreRemainingEvents()
            }
        }
```

Add imports `se.birdy.ml.FrameFormat` and `se.birdy.ml.ImageInput` to the test file if missing.

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.app.ui.scan.ScanViewModelTest" 2>&1 | tail -20`
Expected: FAIL — type mismatch (lambda `(ImageInput) -> String` against `(ByteArray) -> String`) or `received?.timestampMillis` unresolved on `ByteArray`.

- [ ] **Step 3: Change the signature in ScanViewModel + ScanScreen + Android host**

`ScanViewModel.kt` line 148-151:

```kotlin
    fun onFreeze(persist: (ImageInput) -> String) {
        if (_state.value is ScanUiState.FrozenAt) return
        val snap = lastClassified ?: return
        val path = runCatching { persist(snap.frame) }.getOrNull() ?: return
```

`ScanScreen.kt` line 99: `persistFrame: (ImageInput) -> String,` and add `import se.birdy.ml.ImageInput`. Call sites (`viewModel.onFreeze(persistFrame)` at lines 146 and 152) are unchanged.

`ScanScreenHost.android.kt` lines 44-48:

```kotlin
        persistFrame = { input ->
            val file = File(cacheDir, UUID.randomUUID().toString() + ".jpg")
            file.outputStream().use { it.write(input.bytes) }
            file.absolutePath
        },
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.app.ui.scan.ScanViewModelTest" --stacktrace`
Expected: PASS (all 12 tests).

- [ ] **Step 5: Full Android gate**

Run: `./gradlew :shared:domain:jvmTest :shared:ml:jvmTest :composeApp:testDebugUnitTest :androidApp:assembleDebug ktlintCheck detekt --stacktrace`
Expected: all green.

- [ ] **Step 6: Commit**

```bash
git add composeApp
git commit -m "refactor(scan): persistFrame takes the full ImageInput — iOS freeze needs format+dims (i2c T3)"
```

---

### Task 4: Freeze-time BGRA→JPEG encode + scan-frame persist (iOS)

**Files:**
- Create: `composeApp/src/iosMain/kotlin/se/birdy/app/ui/scan/IosScanFramePersist.kt`
- Create: `composeApp/src/iosTest/kotlin/se/birdy/app/ui/scan/IosScanFramePersistTest.kt` (new source dir `composeApp/src/iosTest/` — the default hierarchy template provides the `iosTest` source set exactly as in `shared/ml`)

**Interfaces:**
- Consumes: `ImageInput`, `FrameFormat.BGRA_8888` (Task 1); `toNSData()`/`toByteArray()` (internal, `se.birdy.app` — import like `IosImageDecode.kt` does).
- Produces: `internal fun encodeBgraFrameToJpeg(input: ImageInput): ByteArray?` and `internal fun persistScanFrame(input: ImageInput): String` (throws on failure — `ScanViewModel.onFreeze`'s `runCatching` aborts the freeze, exact Android behavior). Task 5's host calls `persistScanFrame`.

- [ ] **Step 1: Write the failing test**

Create `composeApp/src/iosTest/kotlin/se/birdy/app/ui/scan/IosScanFramePersistTest.kt`:

```kotlin
package se.birdy.app.ui.scan

import platform.Foundation.NSFileManager
import se.birdy.ml.FrameFormat
import se.birdy.ml.ImageInput
import se.birdy.ml.ImagePreprocessor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Freeze-encode-kontraktet (i2c): BGRA-frame-bytes → upprätt JPEG med bevarade färger
 * och dimensioner. Färgkontrollen avkodar via shared/ml:s ImagePreprocessor (JPEG-vägen)
 * med identitetsnormalisering (mean=0, std=1/255 → floats == pixelbytes).
 */
class IosScanFramePersistTest {
    // 64×32: vänster halva röd, höger halva blå — BGRA-layout (B,G,R,A).
    private fun testFrame(): ImageInput {
        val w = 64
        val h = 32
        val bytes = ByteArray(w * h * 4)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val o = (y * w + x) * 4
                if (x < w / 2) {
                    bytes[o + 2] = 0xFF.toByte() // R
                } else {
                    bytes[o] = 0xFF.toByte() // B
                }
                bytes[o + 3] = 0xFF.toByte()
            }
        }
        return ImageInput(bytes, widthPx = w, heightPx = h, format = FrameFormat.BGRA_8888, timestampMillis = 1L)
    }

    private fun decodedPixel(
        jpeg: ByteArray,
        w: Int,
        h: Int,
        x: Int,
        y: Int,
    ): Triple<Int, Int, Int> {
        val floats =
            ImagePreprocessor().preprocess(
                ImageInput(jpeg, w, h, format = FrameFormat.JPEG),
                outHeight = h,
                outWidth = w,
                normalizationMean = floatArrayOf(0f, 0f, 0f),
                normalizationStd = floatArrayOf(1 / 255f, 1 / 255f, 1 / 255f),
            )
        val o = (y * w + x) * 3
        return Triple(floats[o].toInt(), floats[o + 1].toInt(), floats[o + 2].toInt())
    }

    @Test
    fun encode_preserves_dimensions_and_channel_order() {
        val jpeg = encodeBgraFrameToJpeg(testFrame())
        assertNotNull(jpeg, "encode returned null")
        // Sampla mitt i varje halva (JPEG-artefakter vid kanterna — tolerans 40/255).
        val (lr, lg, lb) = decodedPixel(jpeg, 64, 32, x = 16, y = 16)
        val (rr, rg, rb) = decodedPixel(jpeg, 64, 32, x = 48, y = 16)
        assertTrue(lr > 200 && lb < 60, "left half must decode red-ish, got rgb($lr,$lg,$lb)")
        assertTrue(rb > 200 && rr < 60, "right half must decode blue-ish, got rgb($rr,$rg,$rb)")
    }

    @Test
    fun persist_writes_a_readable_jpg_and_returns_its_path() {
        val path = persistScanFrame(testFrame())
        assertTrue(path.endsWith(".jpg"), "expected .jpg path, got $path")
        assertTrue(NSFileManager.defaultManager.fileExistsAtPath(path), "file must exist at $path")
        NSFileManager.defaultManager.removeItemAtPath(path, null)
    }

    @Test
    fun encode_rejects_wrong_size_and_wrong_format() {
        val bad = ImageInput(ByteArray(10), widthPx = 64, heightPx = 32, format = FrameFormat.BGRA_8888)
        assertEquals(null, encodeBgraFrameToJpeg(bad))
        val jpegInput = ImageInput(ByteArray(100), widthPx = 5, heightPx = 5, format = FrameFormat.JPEG)
        assertEquals(null, encodeBgraFrameToJpeg(jpegInput))
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :composeApp:compileTestKotlinIosSimulatorArm64 --stacktrace 2>&1 | tail -20`
Expected: FAIL — `Unresolved reference 'encodeBgraFrameToJpeg'`.

**If instead PRE-EXISTING commonTest tests fail to compile/run on the iOS target** (composeApp commonTest has never been built for Kotlin/Native — resource- or dispatcher-bound tests may not port): STOP and report to the orchestrator with the exact failure list. Do not silently exclude tests. (Known risk from the spec's CI note; the decision on scoping belongs to the orchestrator, not this task.)

- [ ] **Step 3: Implement encode + persist**

Create `composeApp/src/iosMain/kotlin/se/birdy/app/ui/scan/IosScanFramePersist.kt`:

```kotlin
@file:OptIn(ExperimentalForeignApi::class)

package se.birdy.app.ui.scan

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGBitmapContextCreateImage
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.kCGBitmapByteOrder32Little
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDomainMask
import platform.Foundation.writeToFile
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import se.birdy.app.toByteArray
import se.birdy.app.toNSData
import se.birdy.ml.FrameFormat
import se.birdy.ml.ImageInput

// Matchar Androids YuvToJpeg-kvalitet (85) för fryst-frame-persist.
private const val FREEZE_JPEG_QUALITY = 0.85

/**
 * Encodar en [FrameFormat.BGRA_8888]-frame till JPEG via CGBitmapContext. BGRA är
 * CoreGraphics native-layout: `byteOrder32Little + premultipliedFirst` (alpha är 255
 * från kameran → premultiply är no-op). Returnerar null på fel format/storlek eller
 * CG-fel — aldrig en tyst felaktig bild.
 *
 * Källbytesen hålls pinnade genom HELA operationen inkl. [UIImageJPEGRepresentation]:
 * CGBitmapContextCreateImage kan backa sina pixlar copy-on-write mot vår buffert
 * (samma resonemang som ImagePreprocessor.ios.kt:s scaleRgba, i2b).
 */
internal fun encodeBgraFrameToJpeg(input: ImageInput): ByteArray? {
    if (input.format != FrameFormat.BGRA_8888) return null
    val expected = input.widthPx * input.heightPx * 4
    if (input.bytes.size != expected) return null
    val colorSpace = CGColorSpaceCreateDeviceRGB()
    try {
        return input.bytes.usePinned { pinned ->
            val ctx =
                CGBitmapContextCreate(
                    pinned.addressOf(0),
                    input.widthPx.convert(),
                    input.heightPx.convert(),
                    8.convert(),
                    (input.widthPx * 4).convert(),
                    colorSpace,
                    BGRA_BITMAP_INFO,
                ) ?: return@usePinned null
            try {
                val image = CGBitmapContextCreateImage(ctx) ?: return@usePinned null
                try {
                    val ui = UIImage.imageWithCGImage(image)
                    UIImageJPEGRepresentation(ui, FREEZE_JPEG_QUALITY)?.toByteArray()
                } finally {
                    CGImageRelease(image)
                }
            } finally {
                CGContextRelease(ctx)
            }
        }
    } finally {
        CGColorSpaceRelease(colorSpace)
    }
}

/**
 * Freeze-persist för iOS-hosten: BGRA-frame → JPEG → `NSCachesDirectory/scan-frames/
 * <uuid>.jpg`. Kastar på encode-/skrivfel — ScanViewModel.onFreeze:s runCatching avbryter
 * då frysen, exakt som Android-hostens `file.outputStream().use { … }`.
 */
internal fun persistScanFrame(input: ImageInput): String {
    val jpeg =
        when (input.format) {
            // Defensivt: skulle en redan-JPEG-frame dyka upp (test/fake) persisteras den rakt av.
            FrameFormat.JPEG -> input.bytes
            else ->
                encodeBgraFrameToJpeg(input)
                    ?: error("scan persist: BGRA→JPEG encode failed for ${input.widthPx}x${input.heightPx}")
        }
    val dir = scanFramesDir()
    memScoped {
        val errorVar = alloc<ObjCObjectVar<NSError?>>()
        val created =
            NSFileManager.defaultManager.createDirectoryAtPath(
                path = dir,
                withIntermediateDirectories = true,
                attributes = null,
                error = errorVar.ptr,
            )
        if (!created) {
            error("scan persist: failed to create directory $dir: ${errorVar.value?.localizedDescription}")
        }
    }
    val path = "$dir/${NSUUID().UUIDString}.jpg"
    if (!jpeg.toNSData().writeToFile(path, atomically = true)) {
        error("scan persist: failed to write $path")
    }
    return path
}

private fun scanFramesDir(): String {
    val caches =
        NSFileManager.defaultManager
            .URLsForDirectory(NSCachesDirectory, NSUserDomainMask)
            .firstOrNull() as? NSURL
    val base = caches?.path ?: NSTemporaryDirectory()
    return "$base/scan-frames"
}

// 32-bit BGRA i minnet = little-endian ARGB: byteOrder32Little + premultipliedFirst.
private val BGRA_BITMAP_INFO: UInt =
    CGImageAlphaInfo.kCGImageAlphaPremultipliedFirst.value or kCGBitmapByteOrder32Little
```

- [ ] **Step 4: Run the tests**

Run: `./gradlew :composeApp:iosSimulatorArm64Test --tests "se.birdy.app.ui.scan.IosScanFramePersistTest" --stacktrace`
Expected: PASS. Then run the WHOLE task once — `./gradlew :composeApp:iosSimulatorArm64Test --stacktrace` — to surface any pre-existing commonTest-on-native issues now rather than in CI (Task 8). Same STOP-and-report rule as Step 2.

- [ ] **Step 5: Android regression + lint**

Run: `./gradlew :composeApp:testDebugUnitTest :androidApp:assembleDebug ktlintCheck detekt --stacktrace`
Expected: all green (iosMain/iosTest additions are invisible to Android).

- [ ] **Step 6: Commit**

```bash
git add composeApp
git commit -m "feat(ios): freeze-time BGRA→JPEG encode + scan-frame persist (i2c T4)"
```

---

### Task 5: Permission + preview + `ScanScreenHost.ios` + wiring + Info.plist

**Files:**
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/permissions/CameraPermissionStatus.kt`
- Modify: `composeApp/src/androidMain/kotlin/se/birdy/app/permissions/CameraPermission.kt` (remove the enum, now in commonMain)
- Create: `composeApp/src/iosMain/kotlin/se/birdy/app/permissions/IosCameraPermission.kt`
- Modify: `composeApp/src/iosMain/kotlin/se/birdy/app/ui/scan/CameraPreviewHost.ios.kt` (real preview)
- Modify: `composeApp/src/iosMain/kotlin/se/birdy/app/ui/scan/ScanScreenHost.ios.kt` (real host)
- Delete: `composeApp/src/iosMain/kotlin/se/birdy/app/ui/scan/IosNoopCameraSource.kt`
- Modify: `composeApp/src/iosMain/kotlin/se/birdy/app/IosAppGraph.kt` (factory + versionName)
- Modify: `iosApp/iosApp/Info.plist` (+ create `iosApp/iosApp/en.lproj/InfoPlist.strings`, `iosApp/iosApp/sv.lproj/InfoPlist.strings`)

**Interfaces:**
- Consumes: `IosCameraSource` + `captureSession` (Task 2), `persistScanFrame` (Task 4), `persistFrame: (ImageInput) -> String` (Task 3), shared `ScanScreen`/`ScanViewModel`/`AppGraph.scanViewModel()`.
- Produces: `enum class CameraPermissionStatus` in commonMain (`se.birdy.app.permissions`, same name/values as before — Android code compiles unchanged); `rememberIosCameraPermissionState(): IosCameraPermissionState` with `status`/`launchRequest()`/`openAppSettings()`.

- [ ] **Step 1: Move `CameraPermissionStatus` to commonMain**

Create `composeApp/src/commonMain/kotlin/se/birdy/app/permissions/CameraPermissionStatus.kt`:

```kotlin
package se.birdy.app.permissions

/** Delad permission-status för kamerarelaterade hosts (Android runtime-permission / iOS AVCaptureDevice). */
enum class CameraPermissionStatus { Granted, Denied, NotAsked }
```

Delete the `enum class CameraPermissionStatus { Granted, Denied, NotAsked }` line from `composeApp/src/androidMain/kotlin/se/birdy/app/permissions/CameraPermission.kt` (line 23). Same package → no import changes anywhere.

Run: `./gradlew :composeApp:testDebugUnitTest :androidApp:assembleDebug --stacktrace` — Expected: green.

- [ ] **Step 2: Implement `IosCameraPermission`**

Create `composeApp/src/iosMain/kotlin/se/birdy/app/permissions/IosCameraPermission.kt` (mirror of `CameraPermission.kt`):

```kotlin
package se.birdy.app.permissions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusDenied
import platform.AVFoundation.AVAuthorizationStatusRestricted
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * iOS-spegel av Androids [CameraPermissionState]: AVCaptureDevice-auktorisering med
 * samma trestatus-mappning (.authorized→Granted, .denied/.restricted→Denied,
 * .notDetermined→NotAsked) och omkontroll när appen blir aktiv igen — fångar
 * "användaren flippade togglen i Inställningar", precis som Androids ON_RESUME-observer.
 */
class IosCameraPermissionState(
    private val statusState: MutableState<CameraPermissionStatus>,
    private val request: () -> Unit,
    private val openSettings: () -> Unit,
) {
    val status: CameraPermissionStatus get() = statusState.value

    fun launchRequest() = request()

    fun openAppSettings() = openSettings()
}

@Composable
fun rememberIosCameraPermissionState(): IosCameraPermissionState {
    val statusState = remember { mutableStateOf(computeStatus()) }
    DisposableEffect(Unit) {
        val observer =
            NSNotificationCenter.defaultCenter.addObserverForName(
                name = UIApplicationDidBecomeActiveNotification,
                `object` = null,
                queue = NSOperationQueue.mainQueue,
            ) { _ -> statusState.value = computeStatus() }
        onDispose { NSNotificationCenter.defaultCenter.removeObserver(observer) }
    }
    return remember {
        IosCameraPermissionState(
            statusState = statusState,
            request = {
                AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
                    dispatch_async(dispatch_get_main_queue()) {
                        statusState.value =
                            if (granted) CameraPermissionStatus.Granted else CameraPermissionStatus.Denied
                    }
                }
            },
            openSettings = {
                val url = NSURL(string = UIApplicationOpenSettingsURLString)
                UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any?>(), completionHandler = null)
            },
        )
    }
}

private fun computeStatus(): CameraPermissionStatus =
    when (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)) {
        AVAuthorizationStatusAuthorized -> CameraPermissionStatus.Granted
        AVAuthorizationStatusDenied, AVAuthorizationStatusRestricted -> CameraPermissionStatus.Denied
        else -> CameraPermissionStatus.NotAsked
    }
```

(If `openURL(url, options, completionHandler)` does not resolve with that overload, use the K/N signature `openURL(url!!, emptyMap<Any?, Any>(), null)`; check the generated stubs.)

- [ ] **Step 3: Implement the preview actual**

Replace `composeApp/src/iosMain/kotlin/se/birdy/app/ui/scan/CameraPreviewHost.ios.kt`:

```kotlin
@file:OptIn(ExperimentalForeignApi::class)

package se.birdy.app.ui.scan

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureVideoOrientationPortrait
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.CoreGraphics.CGRectZero
import platform.QuartzCore.CATransaction
import platform.UIKit.UIView
import se.birdy.ml.CameraSource
import se.birdy.ml.camera.IosCameraSource

/**
 * AVCaptureVideoPreviewLayer i en UIKitView. Downcast-mönstret speglar Android-actualen
 * (CameraPreviewHost.android.kt): preview-lagret får känna till sin konkreta källa.
 * resizeAspectFill = Androids PreviewView.FILL_CENTER. Sessionen är eagert skapad i
 * IosCameraSource, så lagret kan kopplas innan start() hunnit konfigurera den.
 */
@Composable
actual fun CameraPreviewHost(
    cameraSource: CameraSource,
    modifier: Modifier,
) {
    val iosSource = cameraSource as? IosCameraSource ?: return
    UIKitView(
        factory = { CameraPreviewView(iosSource.captureSession) },
        modifier = modifier,
    )
}

private class CameraPreviewView(
    session: AVCaptureSession,
) : UIView(frame = CGRectZero.readValue()) {
    private val previewLayer =
        AVCaptureVideoPreviewLayer(session = session).also {
            it.videoGravity = AVLayerVideoGravityResizeAspectFill
        }

    init {
        layer.addSublayer(previewLayer)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        previewLayer.setFrame(bounds)
        // Connection finns först när sessionen fått sina in-/utgångar (efter start()) —
        // portrait sätts därför lazy här, varje layout-pass är billig och idempotent.
        previewLayer.connection?.let { conn ->
            if (conn.isVideoOrientationSupported()) {
                conn.videoOrientation = AVCaptureVideoOrientationPortrait
            }
        }
        CATransaction.commit()
    }
}
```

(`UIKitView` import is `androidx.compose.ui.viewinterop.UIKitView` in CMP 1.8.2 — if the compiler points to the deprecated `androidx.compose.ui.interop` package instead, prefer the non-deprecated one it suggests.)

- [ ] **Step 4: Implement the host actual + wiring**

Replace `composeApp/src/iosMain/kotlin/se/birdy/app/ui/scan/ScanScreenHost.ios.kt`:

```kotlin
package se.birdy.app.ui.scan

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import se.birdy.app.di.AppGraph
import se.birdy.app.permissions.CameraPermissionStatus
import se.birdy.app.permissions.rememberIosCameraPermissionState

@Composable
actual fun ScanScreenHost(
    graph: AppGraph,
    onPhotoAnalyzeClick: () -> Unit,
    onFrozen: (sourceJson: String, capturedAtMs: Long) -> Unit,
    onBack: () -> Unit,
) {
    val permission = rememberIosCameraPermissionState()
    val viewModel = viewModel { graph.scanViewModel() }
    val cameraSource = viewModel.cameraSource

    LaunchedEffect(permission.status) {
        when (permission.status) {
            CameraPermissionStatus.Granted -> viewModel.onPermissionResult(granted = true)
            CameraPermissionStatus.Denied -> viewModel.onPermissionResult(granted = false)
            CameraPermissionStatus.NotAsked -> Unit
        }
    }

    ScanScreen(
        viewModel = viewModel,
        cameraSource = cameraSource,
        onPhotoAnalyzeClick = onPhotoAnalyzeClick,
        onFrozen = onFrozen,
        onBack = onBack,
        onPermissionRequest = { permission.launchRequest() },
        onOpenSettings = { permission.openAppSettings() },
        persistFrame = { input -> persistScanFrame(input) },
    )
}
```

Delete `composeApp/src/iosMain/kotlin/se/birdy/app/ui/scan/IosNoopCameraSource.kt`.

In `IosAppGraph.kt`: replace import `se.birdy.app.ui.scan.IosNoopCameraSource` with `se.birdy.ml.camera.IosCameraSource`; line 90 → `cameraSourceFactory = { IosCameraSource() },`; line 99 → `versionName = "1.2.0-ios-i2c",`. Update the KDoc stub ledger (lines 35-38): remove the live-camera bullet, note i2c resolved it (only the premium override remains).

- [ ] **Step 5: Info.plist + localized usage strings**

In `iosApp/iosApp/Info.plist` add (inside the top-level `<dict>`):

```xml
    <key>NSCameraUsageDescription</key>
    <string>Birdy uses the camera to identify birds in real time. Frames are analyzed on your device and never leave it.</string>
```

Create `iosApp/iosApp/en.lproj/InfoPlist.strings`:

```
"NSCameraUsageDescription" = "Birdy uses the camera to identify birds in real time. Frames are analyzed on your device and never leave it.";
```

Create `iosApp/iosApp/sv.lproj/InfoPlist.strings`:

```
"NSCameraUsageDescription" = "Birdy använder kameran för att identifiera fåglar i realtid. Bilderna analyseras på din enhet och lämnar den aldrig.";
```

Regenerate the Xcode project: `cd iosApp && xcodegen generate && cd ..`
Then verify the lproj files landed as a variant group: `grep -c "InfoPlist.strings" iosApp/Birdy.xcodeproj/project.pbxproj` — expected ≥ 1. If xcodegen did NOT pick them up, keep the Info.plist base string (EN) and delete the lproj files + log the SV localization as a deferred minor — do not fight xcodegen.

- [ ] **Step 6: Gates — link + iOS tests + Android + boot**

```bash
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64 --stacktrace
./gradlew :composeApp:iosSimulatorArm64Test :shared:ml:iosSimulatorArm64Test --stacktrace
./gradlew :shared:domain:jvmTest :shared:ml:jvmTest :composeApp:testDebugUnitTest :androidApp:assembleDebug ktlintCheck detekt --stacktrace
```
Expected: all green.

Boot-verify in the simulator (recipe details in local memory `~/.claude/projects/-Users-albinabrahamsson-dev-birdy/memory/reference_ios_simulator_build_and_verify.md` — CLI build needs a concrete destination):

```bash
UDID=$(xcrun simctl list devices available | grep -m1 -oE '[0-9A-F-]{36}')
xcrun simctl boot "$UDID" 2>/dev/null || true
xcodebuild -project iosApp/Birdy.xcodeproj -scheme Birdy -destination "id=$UDID" -quiet build
APP=$(find ~/Library/Developer/Xcode/DerivedData -name "Birdy.app" -path "*iphonesimulator*" | head -1)
xcrun simctl install "$UDID" "$APP"
xcrun simctl launch "$UDID" se.birdy.ios
sleep 8
xcrun simctl io "$UDID" screenshot docs/superpowers/screenshots/i2c-01-ios-boot.png
```
Expected: app boots to Field Journal UI, no crash (agent cannot tap — scan-tab render is Albin's manual sim check, listed in Task 9). Verify the screenshot is not a black/crash screen before committing it.

- [ ] **Step 7: Commit**

```bash
git add composeApp iosApp docs/superpowers/screenshots/i2c-01-ios-boot.png
git commit -m "feat(ios): live scan wired — permission + AVCapture preview + ScanScreenHost + camera usage strings (i2c T5)"
```

---

### Task 6: Take-photo via the system camera (`UIImagePickerController`)

**Files:**
- Create: `composeApp/src/iosMain/kotlin/se/birdy/app/ui/photoanalyze/IosCameraCapture.kt`
- Modify: `composeApp/src/iosMain/kotlin/se/birdy/app/ui/photoanalyze/IosImageDecode.kt` (`keyWindowRootViewController` + `retainedPickerDelegates`: `private` → `internal`)
- Modify: `composeApp/src/iosMain/kotlin/se/birdy/app/ui/photoanalyze/PhotoAnalyzeHost.ios.kt:116-117` (`onTakePhoto`)

**Interfaces:**
- Consumes: `keyWindowRootViewController()` + `retainedPickerDelegates` (IosImageDecode.kt, made internal), `toByteArray()`.
- Produces: `internal fun isCameraCaptureAvailable(): Boolean`; `internal fun presentCameraCapture(onBytes: (ByteArray?) -> Unit, onPresentFailure: () -> Unit)` — `onBytes(null)` = user cancelled; `onPresentFailure` = present-time failure (no root VC), a distinct signal, mirroring `presentPhotoPicker`.

- [ ] **Step 1: Widen visibility of the two i2b helpers**

In `IosImageDecode.kt`: `private val retainedPickerDelegates` → `internal val retainedPickerDelegates` (line 245) and `private fun keyWindowRootViewController()` → `internal fun keyWindowRootViewController()` (line 277). Keep both KDoc comments.

- [ ] **Step 2: Implement the capture presenter**

Create `composeApp/src/iosMain/kotlin/se/birdy/app/ui/photoanalyze/IosCameraCapture.kt`:

```kotlin
package se.birdy.app.ui.photoanalyze

import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import se.birdy.app.toByteArray

// Samma kvalitet som crop-vägens JPEG_QUALITY (IosImageDecode.kt) — bytesen går in i
// exakt samma decodeForCrop-pipeline som galleri-picken.
private const val CAPTURE_JPEG_QUALITY = 0.9

/** False i simulatorn (ingen kamera) → ta-foto-knappen är en tyst no-op där (spec §5). */
internal fun isCameraCaptureAvailable(): Boolean =
    UIImagePickerController.isSourceTypeAvailable(
        UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera,
    )

/**
 * Presenterar systemkameran (UIImagePickerController, iOS-motsvarigheten till Androids
 * TakePicture-intent). [onBytes] får JPEG-bytes (EXIF-orientering bevarad — decodeForCrop:s
 * bakeUpright hanterar den) eller null vid avbrutet val; [onPresentFailure] anropas synkront
 * om ingen root-VC fanns. Delegaten strong-retainas i [retainedPickerDelegates] —
 * `.delegate` är weak (i2b-trapen).
 */
internal fun presentCameraCapture(
    onBytes: (ByteArray?) -> Unit,
    onPresentFailure: () -> Unit,
) {
    val root = keyWindowRootViewController()
    if (root == null) {
        onPresentFailure()
        return
    }
    val picker = UIImagePickerController()
    picker.sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
    val delegate = CameraCaptureDelegate(onBytes)
    retainedPickerDelegates.add(delegate)
    picker.delegate = delegate
    root.presentViewController(picker, animated = true, completion = null)
}

private class CameraCaptureDelegate(
    private val onBytes: (ByteArray?) -> Unit,
) : NSObject(),
    UIImagePickerControllerDelegateProtocol,
    UINavigationControllerDelegateProtocol {
    override fun imagePickerController(
        picker: UIImagePickerController,
        didFinishPickingMediaWithInfo: Map<Any?, *>,
    ) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
        finish(image?.let { UIImageJPEGRepresentation(it, CAPTURE_JPEG_QUALITY)?.toByteArray() })
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true, completion = null)
        finish(null)
    }

    private fun finish(bytes: ByteArray?) {
        dispatch_async(dispatch_get_main_queue()) {
            retainedPickerDelegates.remove(this)
            onBytes(bytes)
        }
    }
}
```

- [ ] **Step 3: Wire `onTakePhoto` in the host**

In `PhotoAnalyzeHost.ios.kt`, replace lines 116-117:

```kotlin
            onTakePhoto = {
                if (isCameraCaptureAvailable()) {
                    presentCameraCapture(
                        onBytes = { bytes -> if (bytes != null) pendingBytes.value = bytes },
                        onPresentFailure = { viewModel.decodeFailed() },
                    )
                }
                // Ingen kamera (simulator): tyst no-op — knappen förblir inert som i i2b (spec §5).
            },
```

Update the file's KDoc (lines 20-21): take-photo is no longer deferred — it presents the system camera; gallery + camera converge on the same `pendingBytes` → decode → crop → analyze path.

- [ ] **Step 4: Gates**

```bash
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64 :composeApp:iosSimulatorArm64Test ktlintCheck detekt --stacktrace
./gradlew :composeApp:testDebugUnitTest :androidApp:assembleDebug --stacktrace
```
Expected: all green (camera capture is not sim-testable — no camera; the code path is compile-verified + device-gated).

- [ ] **Step 5: Commit**

```bash
git add composeApp
git commit -m "feat(ios): take-photo via system camera into the shared crop/analyze path (i2c T6)"
```

---

### Task 7: `IosPhotoStorage` — the 1024 px rescale debt

**Files:**
- Modify: `composeApp/src/iosMain/kotlin/se/birdy/app/ui/photoanalyze/IosImageDecode.kt` (`drawAndEncodeJpeg` + `scaleToLongSide`: `private` → `internal`, quality parameter)
- Modify: `composeApp/src/iosMain/kotlin/se/birdy/app/photo/PhotoStorageProvider.ios.kt`
- Create: `composeApp/src/iosTest/kotlin/se/birdy/app/photo/IosPhotoStorageTest.kt`

**Interfaces:**
- Consumes: `drawAndEncodeJpeg(image, targetW, targetH, quality)` + `scaleToLongSide(w, h, target)` (made internal), `encodeBgraFrameToJpeg` (Task 4, test input generation), `FrameUnavailableException`.
- Produces: `IosPhotoStorage.persistJpeg` fulfilling the common contract: decode → longest side ≤ 1024 @ q85 → `Documents/observations/<uuid>.jpg`; throws `FrameUnavailableException` on empty/undecodable bytes.

- [ ] **Step 1: Write the failing test**

Create `composeApp/src/iosTest/kotlin/se/birdy/app/photo/IosPhotoStorageTest.kt`:

```kotlin
package se.birdy.app.photo

import kotlinx.coroutines.test.runTest
import platform.Foundation.NSFileManager
import se.birdy.app.ui.photoanalyze.readJpegPixelSize
import se.birdy.app.ui.scan.encodeBgraFrameToJpeg
import se.birdy.ml.FrameFormat
import se.birdy.ml.ImageInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** i2c: IosPhotoStorage måste nu uppfylla hela kontraktet — longest side ≤1024 @ q85. */
class IosPhotoStorageTest {
    private fun jpegOfSize(
        w: Int,
        h: Int,
    ): ByteArray {
        val bytes = ByteArray(w * h * 4) { i -> if (i % 4 == 3) 0xFF.toByte() else 0x80.toByte() }
        return assertNotNull(
            encodeBgraFrameToJpeg(ImageInput(bytes, w, h, format = FrameFormat.BGRA_8888)),
            "test fixture encode failed",
        )
    }

    @Test
    fun oversized_input_is_rescaled_to_1024_longest_side() =
        runTest {
            val storage = IosPhotoStorage()
            val path = storage.persistJpeg(jpegOfSize(1500, 900))
            try {
                val (w, h) = assertNotNull(readJpegPixelSize(path), "persisted file must decode")
                assertEquals(1024, w, "longest side must be exactly 1024")
                assertEquals(614, h, "short side must keep aspect (900*1024/1500 ≈ 614)")
            } finally {
                storage.delete(path)
            }
        }

    @Test
    fun small_input_keeps_its_dimensions() =
        runTest {
            val storage = IosPhotoStorage()
            val path = storage.persistJpeg(jpegOfSize(640, 480))
            try {
                val (w, h) = assertNotNull(readJpegPixelSize(path))
                assertEquals(640 to 480, w to h, "no upscaling")
                assertTrue(NSFileManager.defaultManager.fileExistsAtPath(path))
            } finally {
                storage.delete(path)
            }
        }

    @Test
    fun empty_and_undecodable_bytes_throw_frame_unavailable() =
        runTest {
            val storage = IosPhotoStorage()
            assertFailsWith<FrameUnavailableException> { storage.persistJpeg(ByteArray(0)) }
            assertFailsWith<FrameUnavailableException> { storage.persistJpeg(ByteArray(64) { 1 }) }
        }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :composeApp:compileTestKotlinIosSimulatorArm64 --stacktrace 2>&1 | tail -20`
Expected: FAIL — `Unresolved reference 'readJpegPixelSize'`.

- [ ] **Step 3: Implement**

In `IosImageDecode.kt`:
- `private fun drawAndEncodeJpeg(image, targetW, targetH)` → `internal fun drawAndEncodeJpeg(image: UIImage, targetW: Int, targetH: Int, quality: Double = JPEG_QUALITY)` and pass `quality` to `UIImageJPEGRepresentation(baked, quality)`. **Keep the `kCGInterpolationMedium` line and its comment untouched** (i2b final-review trap — it was silently dropped once already).
- `private fun scaleToLongSide` → `internal fun scaleToLongSide`.
- Add a small test/impl helper next to them:

```kotlin
/** Läser pixelmåtten ur en JPEG-fil på disk (test + storage-verifiering). */
internal fun readJpegPixelSize(path: String): Pair<Int, Int>? {
    val data = NSData.dataWithContentsOfFile(path) ?: return null
    val image = UIImage(data = data) ?: return null
    val (w, h) = image.size.useContents { width to height }
    if (w <= 0.0 || h <= 0.0) return null
    return w.roundToInt() to h.roundToInt()
}
```

(add `import platform.Foundation.NSData` and `platform.Foundation.dataWithContentsOfFile`.)

Replace `IosPhotoStorage` in `PhotoStorageProvider.ios.kt`:

```kotlin
private const val LONGEST_SIDE_PX = 1024
private const val PERSIST_JPEG_QUALITY = 0.85

/**
 * iOS impl av [PhotoStorage]-kontraktet (i2c): decode → longest side ≤1024 @ q85 →
 * `Documents/observations/<uuid>.jpg`. Spegel av AndroidPhotoStorage; skalningen
 * återanvänder IosImageDecode:s drawAndEncodeJpeg (kCGInterpolationMedium — parity-valet).
 */
@OptIn(ExperimentalForeignApi::class)
class IosPhotoStorage : PhotoStorage {
    override suspend fun persistJpeg(bytes: ByteArray): String =
        withContext(Dispatchers.Default) {
            if (bytes.isEmpty()) throw FrameUnavailableException("Empty JPEG bytes")
            val image =
                UIImage(data = bytes.toNSData())
                    ?: throw FrameUnavailableException("Undecodable JPEG bytes")
            val (w, h) = image.size.useContents { width to height }
            if (w <= 0.0 || h <= 0.0) throw FrameUnavailableException("Degenerate image ${w}x$h")
            val (targetW, targetH) = scaleToLongSide(w.roundToInt(), h.roundToInt(), LONGEST_SIDE_PX)
            val jpeg =
                drawAndEncodeJpeg(image, targetW, targetH, quality = PERSIST_JPEG_QUALITY)
                    ?: throw FrameUnavailableException("JPEG re-encode failed")
            val docs =
                NSFileManager.defaultManager
                    .URLsForDirectory(NSDocumentDirectory, NSUserDomainMask)
                    .first() as NSURL
            val dir = docs.path + "/observations"
            NSFileManager.defaultManager.createDirectoryAtPath(dir, true, null, null)
            val path = "$dir/${NSUUID().UUIDString}.jpg"
            if (!jpeg.toNSData().writeToFile(path, true)) error("photo persist: failed to write $path")
            path
        }

    override suspend fun delete(path: String) {
        NSFileManager.defaultManager.removeItemAtPath(path, null)
    }
}
```

(imports to add: `kotlinx.cinterop.useContents`, `kotlinx.coroutines.Dispatchers`, `kotlinx.coroutines.withContext`, `platform.UIKit.UIImage`, `se.birdy.app.ui.photoanalyze.drawAndEncodeJpeg`, `se.birdy.app.ui.photoanalyze.scaleToLongSide`, `kotlin.math.roundToInt`. Note: `drawAndEncodeJpeg` draws orientation-respecting, so EXIF-oriented inputs come out upright — strictly better than persisting them as-is.)

- [ ] **Step 4: Run the tests**

Run: `./gradlew :composeApp:iosSimulatorArm64Test --tests "se.birdy.app.photo.IosPhotoStorageTest" --stacktrace`
Expected: PASS. (If the 614 assertion is off by one due to rounding, read the actual and fix the EXPECTATION only if it matches `scaleToLongSide`'s documented rounding — `(900 * 1024/1500).roundToInt() = 614` — never widen to a range.)

- [ ] **Step 5: Full iOS + Android gates**

Run: `./gradlew :composeApp:iosSimulatorArm64Test :composeApp:testDebugUnitTest :androidApp:assembleDebug ktlintCheck detekt --stacktrace`
Expected: all green.

- [ ] **Step 6: Commit**

```bash
git add composeApp
git commit -m "feat(ios): IosPhotoStorage honors the 1024px/q85 contract (i2c T7 — pays the i1 debt)"
```

---

### Task 8: CI — run composeApp's iOS tests in the macOS job

**Files:**
- Modify: `.github/workflows/ci.yml:78-79`

**Interfaces:**
- Consumes: `:composeApp:iosSimulatorArm64Test` (Tasks 4-7 populated it).
- Produces: CI coverage for every iOS test in this plan.

- [ ] **Step 1: Add the target to the iOS test step**

In `.github/workflows/ci.yml`, extend line 79:

```yaml
      - name: iOS unit tests (shared modules + composeApp)
        run: ./gradlew :shared:content:iosSimulatorArm64Test :shared:domain:iosSimulatorArm64Test :shared:data:iosSimulatorArm64Test :shared:ml:iosSimulatorArm64Test :composeApp:iosSimulatorArm64Test --stacktrace
```

- [ ] **Step 2: Validate + local proof**

Run: `./gradlew :composeApp:iosSimulatorArm64Test --stacktrace` — Expected: PASS (same command CI will run).

- [ ] **Step 3: Commit (workflow scope needed on push — resolved in i0; if push is rejected for scope, STOP and report)**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: run :composeApp:iosSimulatorArm64Test in the macOS job (i2c T8)"
```

---

### Task 9: Final regression sweep + docs sync

**Files:**
- Modify: `CLAUDE.md` (Status section + plan-of-plans i2c row)
- No code changes — this is the plan-exit gate.

- [ ] **Step 1: Full gate, everything, in order**

```bash
./gradlew :shared:domain:jvmTest :shared:ml:jvmTest :composeApp:testDebugUnitTest :androidApp:assembleDebug ktlintCheck detekt --stacktrace
./gradlew :shared:content:iosSimulatorArm64Test :shared:domain:iosSimulatorArm64Test :shared:data:iosSimulatorArm64Test :shared:ml:iosSimulatorArm64Test :composeApp:iosSimulatorArm64Test --stacktrace
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64 --stacktrace
```
Expected: all green. Re-run the Task 5 simulator boot check if any code changed since; screenshot must show the app booted.

- [ ] **Step 2: Update CLAUDE.md**

In the Status section, add an i2c entry mirroring the i2b entry's style: code complete + reviewed status, what landed (BGRA pipeline, IosCameraSource, hosts, take-photo, PhotoStorage debt, CI), and the OPEN gates: **(a) Albin's manual sim check (no device needed): scan tab → permission panel → Allow → iOS dialog shows the localized usage string → grant → black preview + "searching…" + no crash; take-photo button inert in sim. (b) Milestone 1 device verify (needs the iPhone): real frames end-to-end, FOV zoom 1×/5×/10×, freeze→match→save, take-photo→crop→analyze, permission round-trips, background/foreground resume, low-light sanity — plus the spec's standing caveat that only this verify proves the composed live path.** Update the plan-of-plans i2c row to 🔄 kod klar. Do NOT claim Milestone 1.

- [ ] **Step 3: Commit + push**

```bash
git add CLAUDE.md
git commit -m "docs: i2c code-complete — live camera + take-photo on iOS; device gate open (no iPhone yet)"
git push
```

---

## Self-review notes (already applied)

- **Spec coverage:** §4 components 1-10 → Tasks 2,1,1,5,5,3+5,3,6,7,5. §5 data flow → Tasks 2+4. §6 error handling → encoded in Tasks 2 (silent start-fail, frame skip, format guard), 4 (throwing persist), 6 (cancel/no-op/decodeFailed split). §7 tests 1-7 → Tasks 1,1,2,4,7,3,9 (+ CI in Task 8). Open device gate → Task 9 docs.
- **Deliberate deviation from spec wording:** the spec's parity guard says "decode fixture → permute to BGRA"; the plan's `IosBgraClassifierParityTest` gets the fixture's pixels via identity-normalization preprocessing instead of a new decode hook — same pixels, same real-classifier assertion, zero production test hooks.
- **Type consistency check:** `IosCameraSource.captureSession` (T2) == property read in T5 preview; `persistScanFrame(input)` (T4) == lambda in T5; `CameraPermissionStatus` moved commonMain (T5) keeps package `se.birdy.app.permissions` so the Android host's imports are untouched; `drawAndEncodeJpeg(…, quality)` (T7) default keeps `finalizeCrop`/`bakeUpright` call sites compiling unchanged.
- **Known risk, owned by Steps not vibes:** composeApp commonTest has never run on Kotlin/Native — Tasks 4/7 carry explicit STOP-and-report instructions if pre-existing tests fail on the new target.
