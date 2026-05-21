package se.birdy.app.ui.audio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import se.birdy.ml.AudioClassification
import se.birdy.ml.AudioClassifierMode
import se.birdy.ml.AudioInput
import se.birdy.ml.AudioModelInfo
import se.birdy.ml.BirdAudioClassifier
import se.birdy.ml.ClassificationResult
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class FakeRecorder(
    private val pcm: ShortArray = ShortArray(144_000),
) : AudioRecorderApi {
    override suspend fun record3s(onLevel: (Float) -> Unit): ShortArray {
        repeat(3) { onLevel(0.5f) }
        return pcm
    }
}

private class FakeWaveformRenderer : WaveformRendererApi {
    override suspend fun renderWaveformPng(
        pcm: ShortArray,
        outPath: String,
    ) = outPath

    override suspend fun encodeOpus(
        pcm: ShortArray,
        outPath: String,
    ) = outPath
}

private class FakeAudioClf : BirdAudioClassifier {
    override val info =
        AudioModelInfo(
            modelVersion = "fake_v1",
            inputShape = listOf(1, 144_000),
            outputShape = listOf(1, 6_522),
            coveragePct = 100.0,
        )

    override suspend fun classify(input: AudioInput) =
        AudioClassification(
            results = listOf(ClassificationResult("Q25334", 0.9f)),
            inferenceMs = 5L,
            modelVersion = "fake_v1",
        )

    override fun close() {}
}

@OptIn(ExperimentalCoroutinesApi::class)
class AudioScanViewModelTest {
    /** Stub normalizer that avoids the JVM-only [se.birdy.ml.normalize] platform function. */
    private val stubNormalizer: (ShortArray) -> FloatArray = { FloatArray(it.size) }

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * Creates a VM with all test-safe dependencies injected.
     * [ioDispatcher] uses [Dispatchers.Unconfined] so [withContext] calls
     * in [AudioScanViewModel.analyzeAndNavigate] don't block virtual time.
     */
    private fun makeVm(clock: () -> Long = { System.currentTimeMillis() }) =
        AudioScanViewModel(
            classifierProvider = { Pair(FakeAudioClf(), AudioClassifierMode.REAL) },
            recorder = FakeRecorder(),
            waveformRenderer = FakeWaveformRenderer(),
            audioStorageDir = { "/tmp/audio" },
            clock = clock,
            normalizer = stubNormalizer,
            ioDispatcher = Dispatchers.Unconfined,
        )

    @Test
    fun startRecording_transitionsThroughRecordingAndAnalyzingToNavigate() =
        runTest {
            val vm = makeVm(clock = { 1_000L })
            vm.onPermissionState(PermissionState.Granted)
            assertEquals(AudioScanState.Idle, vm.state.value)

            vm.startRecording()
            advanceUntilIdle()
            assertTrue(vm.state.value is AudioScanState.NavigateToMatch, "got ${vm.state.value}")
        }

    @Test
    fun cancelRecording_returnsToIdle() =
        runTest {
            val vm = makeVm()
            vm.onPermissionState(PermissionState.Granted)
            vm.startRecording()
            vm.cancelRecording()
            assertEquals(AudioScanState.Idle, vm.state.value)
        }

    @Test
    fun permissionDenied_emitsPermissionNeeded() {
        val vm = makeVm()
        vm.onPermissionState(PermissionState.Denied)
        assertEquals(AudioScanState.PermissionNeeded(canRequest = true), vm.state.value)
    }

    @Test
    fun permissionPermanentlyDenied_emitsError() {
        val vm = makeVm()
        vm.onPermissionState(PermissionState.PermanentlyDenied)
        assertEquals(AudioScanState.Error.PermanentlyDenied, vm.state.value)
    }
}
