package se.birdy.app.ui.scan

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import se.birdy.app.testing.FakeCameraSource
import se.birdy.ml.BirdClassifier
import se.birdy.ml.ClassifierMode
import se.birdy.ml.FakeBirdClassifier
import se.birdy.ml.ImageInput
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ScanViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest fun before() = Dispatchers.setMain(dispatcher)

    @AfterTest fun after() = Dispatchers.resetMain()

    @Test
    fun emits_scanning_with_top1_when_above_threshold() =
        runTest(dispatcher) {
            val cameraSource = FakeCameraSource()
            val vm =
                ScanViewModel(
                    classifier = FakeBirdClassifier(),
                    cameraSourceFactory = { cameraSource },
                    frameThrottling = false,
                )
            vm.onPermissionResult(granted = true)
            vm.state.test {
                assertEquals(ScanUiState.Idle, awaitItem())
                cameraSource.emit(timestampMillis = 1L)
                val s = awaitItem()
                assertIs<ScanUiState.Scanning>(s)
                assertEquals("Q25485", s.top1?.speciesId)
                assertTrue((s.top1?.confidence ?: 0f) >= 0.35f)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun emits_scanning_with_null_top1_when_below_threshold() =
        runTest(dispatcher) {
            val cameraSource = FakeCameraSource()
            val vm =
                ScanViewModel(
                    classifier = FakeBirdClassifier(),
                    cameraSourceFactory = { cameraSource },
                    frameThrottling = false,
                )
            vm.onPermissionResult(granted = true)
            vm.state.test {
                assertEquals(ScanUiState.Idle, awaitItem())
                // cycle indices 0..4 above threshold; index 5 = "Q_LOW" at 0.22 below
                repeat(6) { cameraSource.emit(timestampMillis = it.toLong()) }
                var sixth: ScanUiState.Scanning? = null
                repeat(6) {
                    val s = awaitItem()
                    assertIs<ScanUiState.Scanning>(s)
                    if (it == 5) sixth = s
                }
                assertEquals(null, sixth?.top1, "below-threshold prediction must surface as null top1")
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun emits_permission_required_when_not_granted_yet() =
        runTest(dispatcher) {
            val vm =
                ScanViewModel(
                    classifier = FakeBirdClassifier(),
                    cameraSourceFactory = { FakeCameraSource() },
                    frameThrottling = false,
                )
            vm.state.test {
                assertEquals(ScanUiState.PermissionRequired, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun emits_permission_denied_when_user_denies() =
        runTest(dispatcher) {
            val vm =
                ScanViewModel(
                    classifier = FakeBirdClassifier(),
                    cameraSourceFactory = { FakeCameraSource() },
                    frameThrottling = false,
                )
            vm.state.test {
                assertEquals(ScanUiState.PermissionRequired, awaitItem())
                vm.onPermissionResult(granted = false)
                assertEquals(ScanUiState.PermissionDenied, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun freeze_emits_frozen_at_with_path_and_predictions() =
        runTest(dispatcher) {
            val cameraSource = FakeCameraSource()
            val vm =
                ScanViewModel(
                    classifier = FakeBirdClassifier(),
                    cameraSourceFactory = { cameraSource },
                    frameThrottling = false,
                )
            vm.onPermissionResult(granted = true)
            vm.state.test {
                assertEquals(ScanUiState.Idle, awaitItem())
                cameraSource.emit(timestampMillis = 42L)
                assertIs<ScanUiState.Scanning>(awaitItem())

                vm.onFreeze(jpegBytes = byteArrayOf(1, 2, 3)) { _ -> "/cache/scan-frames/test.jpg" }
                val frozen = awaitItem()
                assertIs<ScanUiState.FrozenAt>(frozen)
                assertEquals("/cache/scan-frames/test.jpg", frozen.frameJpegPath)
                assertEquals("Q25485", frozen.predictions.firstOrNull()?.speciesId)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun auto_throttles_when_classifier_p95_above_threshold() =
        runTest(dispatcher) {
            val cameraSource = FakeCameraSource()
            // Fake clock advances by 400ms per frame so p95 will exceed the 333ms threshold.
            var fakeNow = 0L
            val vm =
                ScanViewModel(
                    classifier = SlowClassifier(latencyMs = 400L),
                    cameraSourceFactory = { cameraSource },
                    frameThrottling = false,
                    nowMillis = { fakeNow.also { fakeNow += 400L } },
                )
            vm.onPermissionResult(granted = true)
            vm.state.test {
                assertEquals(ScanUiState.Idle, awaitItem())
                // Emit 10 frames to fill the rolling latency window.
                repeat(10) { cameraSource.emit(timestampMillis = it.toLong()) }
                var lastScanning: ScanUiState.Scanning? = null
                repeat(10) {
                    val s = awaitItem()
                    assertIs<ScanUiState.Scanning>(s)
                    lastScanning = s
                }
                assertEquals(true, lastScanning?.isThrottled, "isThrottled must be true when p95 > 333ms")
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun classifierMode_defaults_to_real_and_round_trips_demo() =
        runTest(dispatcher) {
            val vmReal =
                ScanViewModel(
                    classifier = FakeBirdClassifier(),
                    cameraSourceFactory = { FakeCameraSource() },
                    frameThrottling = false,
                )
            assertEquals(ClassifierMode.REAL, vmReal.classifierMode, "default must be REAL")

            val vmDemo =
                ScanViewModel(
                    classifier = FakeBirdClassifier(),
                    cameraSourceFactory = { FakeCameraSource() },
                    classifierMode = ClassifierMode.DEMO,
                    frameThrottling = false,
                )
            assertEquals(ClassifierMode.DEMO, vmDemo.classifierMode, "DEMO must round-trip via constructor")
        }
}

/** Classifier that delegates to [FakeBirdClassifier] but reports high latency via injected clock. */
private class SlowClassifier(
    @Suppress("UNUSED_PARAMETER") val latencyMs: Long,
) : BirdClassifier {
    private val delegate = FakeBirdClassifier()

    override suspend fun classify(image: ImageInput) = delegate.classify(image)

    override fun close() {}
}
