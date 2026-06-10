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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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

/**
 * Configurable classifier that returns a confidence-per-call sequence
 * and records every input slice it saw.
 */
private class ScriptedClassifier(
    val confidencesPerCall: List<Float>,
    val speciesId: String = "Q25334",
) : BirdAudioClassifier {
    override val info =
        AudioModelInfo(
            modelVersion = "scripted",
            inputShape = listOf(1, 144_000),
            outputShape = listOf(1, 1),
            coveragePct = 100.0,
        )

    val callInputs = mutableListOf<AudioInput>()
    private var idx = 0

    override suspend fun classify(input: AudioInput): AudioClassification {
        callInputs.add(input)
        val confidence = confidencesPerCall.getOrNull(idx) ?: 0f
        idx++
        return AudioClassification(
            results = listOf(ClassificationResult(speciesId, confidence)),
            inferenceMs = 5L,
            modelVersion = "scripted",
        )
    }

    override fun close() {}
}

@OptIn(ExperimentalCoroutinesApi::class)
class AudioScanViewModelTest {
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

    private fun makeVm(
        classifier: BirdAudioClassifier = ScriptedClassifier(listOf(0f)),
        recorder: AudioRecorderApi = FakeStreamingRecorder(),
        clock: () -> Long = { 0L },
    ) = Pair(
        AudioScanViewModel(
            classifierProvider = { Pair(classifier, AudioClassifierMode.REAL) },
            recorder = recorder,
            waveformRenderer = FakeWaveformRenderer(),
            audioStorageDir = { "/tmp/audio" },
            clock = clock,
            normalizer = stubNormalizer,
            ioDispatcher = Dispatchers.Unconfined,
            inferenceDispatcher = Dispatchers.Unconfined,
        ),
        recorder,
    )

    @Test
    fun autoStops_whenConfidenceReachesThreshold_after3s() =
        runTest {
            val classifier = ScriptedClassifier(confidencesPerCall = listOf(0.40f, 0.75f))
            val recorder = FakeStreamingRecorder()
            val (vm, _) = makeVm(classifier = classifier, recorder = recorder)
            vm.onPermissionState(PermissionState.Granted)

            vm.startRecording()
            recorder.emitChunks(120)
            advanceUntilIdle()

            assertTrue(
                vm.state.value is AudioScanState.NavigateToMatch ||
                    vm.state.value is AudioScanState.Analyzing,
                "expected Analyzing/NavigateToMatch, got ${vm.state.value}",
            )
            assertTrue(classifier.callInputs.size >= 2, "classifier called ${classifier.callInputs.size} times")
        }

    @Test
    fun doesNotAutoStop_beforeFirstFullWindowAvailable() =
        runTest {
            val classifier = ScriptedClassifier(confidencesPerCall = listOf(0.99f))
            val recorder = FakeStreamingRecorder()
            val (vm, _) = makeVm(classifier = classifier, recorder = recorder)
            vm.onPermissionState(PermissionState.Granted)

            vm.startRecording()
            recorder.emitChunks(60)
            advanceUntilIdle()

            assertTrue(vm.state.value is AudioScanState.Recording, "got ${vm.state.value}")
            assertEquals(0, classifier.callInputs.size)
        }

    @Test
    fun manualStop_runsFinalClassifyAndNavigates() =
        runTest {
            val classifier = ScriptedClassifier(confidencesPerCall = listOf(0.45f, 0.50f))
            val recorder = FakeStreamingRecorder()
            val (vm, _) = makeVm(classifier = classifier, recorder = recorder)
            vm.onPermissionState(PermissionState.Granted)

            vm.startRecording()
            recorder.emitChunks(150)
            advanceUntilIdle()
            vm.stopRecording()
            advanceUntilIdle()

            assertTrue(vm.state.value is AudioScanState.NavigateToMatch, "got ${vm.state.value}")
        }

    @Test
    fun manualStop_beforeAnyWindowProcessed_fallsBackToLast3s() =
        runTest {
            val classifier = ScriptedClassifier(confidencesPerCall = listOf(0f, 0f, 0f, 0.42f))
            val recorder = FakeStreamingRecorder()
            val (vm, _) = makeVm(classifier = classifier, recorder = recorder)
            vm.onPermissionState(PermissionState.Granted)

            vm.startRecording()
            recorder.emitChunks(120)
            advanceUntilIdle()
            vm.stopRecording()
            advanceUntilIdle()

            assertTrue(vm.state.value is AudioScanState.NavigateToMatch, "got ${vm.state.value}")
            assertNotNull(classifier.callInputs.lastOrNull())
        }

    @Test
    fun capReached_runsFinalClassifyAndNavigates() =
        runTest {
            val classifier = ScriptedClassifier(confidencesPerCall = List(70) { 0.30f })
            val recorder = FakeStreamingRecorder(maxBufferSamples = 60 * 48_000)
            val (vm, _) = makeVm(classifier = classifier, recorder = recorder)
            vm.onPermissionState(PermissionState.Granted)

            vm.startRecording()
            recorder.emitChunks(1800)
            advanceUntilIdle()

            assertTrue(vm.state.value is AudioScanState.NavigateToMatch, "got ${vm.state.value}")
        }

    @Test
    fun cancelRecording_returnsToIdle_andDoesNotCallClassifier() =
        runTest {
            val classifier = ScriptedClassifier(confidencesPerCall = listOf(0.99f))
            val recorder = FakeStreamingRecorder()
            val (vm, _) = makeVm(classifier = classifier, recorder = recorder)
            vm.onPermissionState(PermissionState.Granted)

            vm.startRecording()
            recorder.emitChunks(30)
            vm.cancelRecording()
            advanceUntilIdle()

            assertEquals(AudioScanState.Idle, vm.state.value)
            assertEquals(0, classifier.callInputs.size)
        }

    @Test
    fun bestSoFar_tracksHighestConfidenceAcrossWindows() =
        runTest {
            val classifier = ScriptedClassifier(confidencesPerCall = listOf(0.30f, 0.50f, 0.40f, 0.55f))
            val recorder = FakeStreamingRecorder()
            val (vm, _) = makeVm(classifier = classifier, recorder = recorder)
            vm.onPermissionState(PermissionState.Granted)

            vm.startRecording()
            recorder.emitChunks(200)
            advanceUntilIdle()
            vm.stopRecording()
            advanceUntilIdle()

            assertTrue(
                classifier.callInputs.size >= 3,
                "expected ≥3 classify calls (3 streaming + 1 final), got ${classifier.callInputs.size}",
            )
        }

    @Test
    fun permissionDenied_emitsPermissionNeeded() {
        val (vm, _) = makeVm()
        vm.onPermissionState(PermissionState.Denied)
        assertEquals(AudioScanState.PermissionNeeded, vm.state.value)
    }

    @Test
    fun permissionPermanentlyDenied_emitsError() {
        val (vm, _) = makeVm()
        vm.onPermissionState(PermissionState.PermanentlyDenied)
        assertEquals(AudioScanState.Error.PermanentlyDenied, vm.state.value)
    }
}
