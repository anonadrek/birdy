package se.birdy.app.ui.audio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import se.birdy.ml.AudioClassification
import se.birdy.ml.AudioClassifierMode
import se.birdy.ml.AudioInput
import se.birdy.ml.AudioModelInfo
import se.birdy.ml.BirdAudioClassifier
import se.birdy.ml.ClassificationResult
import se.birdy.ml.ScanSourceSerialization
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
    ): String? = outPath
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

/** Klassificerare med olika resultatlistor per anrop — för ackumulator-tester. */
private class MultiResultClassifier(
    private val resultsPerCall: List<List<ClassificationResult>>,
) : BirdAudioClassifier {
    override val info =
        AudioModelInfo(
            modelVersion = "multi",
            inputShape = listOf(1, 144_000),
            outputShape = listOf(1, 1),
            coveragePct = 100.0,
        )
    var calls = 0
        private set

    override suspend fun classify(input: AudioInput): AudioClassification {
        val results = resultsPerCall.getOrNull(calls) ?: emptyList()
        calls++
        return AudioClassification(results = results, inferenceMs = 5L, modelVersion = "multi")
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
                vm.state.value is AudioScanState.NavigateToMatch,
                "auto-stop must reach NavigateToMatch (Analyzing-hang = L1 self-cancel bug), got ${vm.state.value}",
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
    fun finalize_ranksSessionAccumulatorAcrossWindows_top3InSource() =
        runTest {
            // Fönster 1: A=0.30, Fönster 2: B=0.45 + A=0.20, Fönster 3: C=0.10.
            // Sessionsmax: B=0.45, A=0.30, C=0.10 — i den ordningen i källan.
            val classifier =
                MultiResultClassifier(
                    listOf(
                        listOf(ClassificationResult("QA", 0.30f)),
                        listOf(ClassificationResult("QB", 0.45f), ClassificationResult("QA", 0.20f)),
                        listOf(ClassificationResult("QC", 0.10f)),
                    ),
                )
            val recorder = FakeStreamingRecorder()
            val vm2 =
                AudioScanViewModel(
                    classifierProvider = { Pair(classifier, AudioClassifierMode.REAL) },
                    recorder = recorder,
                    waveformRenderer = FakeWaveformRenderer(),
                    audioStorageDir = { "/tmp/audio" },
                    clock = { 0L },
                    normalizer = stubNormalizer,
                    ioDispatcher = Dispatchers.Unconfined,
                    inferenceDispatcher = Dispatchers.Unconfined,
                )
            vm2.onPermissionState(PermissionState.Granted)
            vm2.startRecording()
            // Exakt 3 fönster: 144k (chunk 90) + 48k (chunk 120) + 48k (chunk 150).
            // Ett 4:e fönster skulle triggas vid chunk 180 (+48k) — håll oss under
            // den gränsen så `classifier.calls` nedan blir deterministiskt 3.
            recorder.emitChunks(165)
            advanceUntilIdle()
            vm2.stopRecording()
            advanceUntilIdle()

            val state = vm2.state.value
            assertTrue(state is AudioScanState.NavigateToMatch, "got $state")
            val source = Json.decodeFromString<ScanSourceSerialization>(state.sourceJson)
            val results = source.classification.results
            assertEquals(listOf("QB", "QA", "QC"), results.map { it.speciesId })
            assertEquals(0.45f, results[0].confidence)
            assertEquals(0.30f, results[1].confidence)
            // Ingen om-klassificering av bästa fönstret: exakt de 3 streaming-anropen.
            assertEquals(3, classifier.calls)
        }

    @Test
    fun finalize_withEmptyAccumulator_runsFallbackClassifyOnLastWindow() =
        runTest {
            // Alla streaming-fönster ger tomma resultat -> ackumulatorn är tom ->
            // finalize kör EN fallback-klassificering på sista fönstret.
            val classifier = MultiResultClassifier(List(10) { emptyList() })
            val recorder = FakeStreamingRecorder()
            val vm =
                AudioScanViewModel(
                    classifierProvider = { Pair(classifier, AudioClassifierMode.REAL) },
                    recorder = recorder,
                    waveformRenderer = FakeWaveformRenderer(),
                    audioStorageDir = { "/tmp/audio" },
                    clock = { 0L },
                    normalizer = stubNormalizer,
                    ioDispatcher = Dispatchers.Unconfined,
                    inferenceDispatcher = Dispatchers.Unconfined,
                )
            vm.onPermissionState(PermissionState.Granted)
            vm.startRecording()
            recorder.emitChunks(120)
            advanceUntilIdle()
            val streamingCalls = classifier.calls
            vm.stopRecording()
            advanceUntilIdle()

            assertTrue(vm.state.value is AudioScanState.NavigateToMatch, "got ${vm.state.value}")
            assertEquals(streamingCalls + 1, classifier.calls)
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

    @Test
    fun analyzeTimeout_emitsAnalyzeFailed_notHang() =
        runTest {
            val slowClassifier =
                object : BirdAudioClassifier {
                    override val info =
                        AudioModelInfo("slow", listOf(1, 144_000), listOf(1, 1), 100.0)

                    override suspend fun classify(input: AudioInput): AudioClassification {
                        kotlinx.coroutines.delay(60_000) // långt över ANALYZE_TIMEOUT_MS
                        return AudioClassification(emptyList(), 5L, "slow")
                    }

                    override fun close() {}
                }
            val recorder = FakeStreamingRecorder()
            val vm =
                AudioScanViewModel(
                    classifierProvider = { Pair(slowClassifier, AudioClassifierMode.REAL) },
                    recorder = recorder,
                    waveformRenderer = FakeWaveformRenderer(),
                    audioStorageDir = { "/tmp/audio" },
                    clock = { 0L },
                    normalizer = stubNormalizer,
                    ioDispatcher = Dispatchers.Unconfined,
                    inferenceDispatcher = Dispatchers.Unconfined,
                )
            vm.onPermissionState(PermissionState.Granted)
            vm.startRecording()
            recorder.emitChunks(120)
            // OBS: streaming-inferensen hänger också på slowClassifier — men den är
            // single-flight (inflight-guard) och blockerar inte stopRecording-vägen.
            vm.stopRecording()
            advanceUntilIdle()

            assertEquals(AudioScanState.Error.AnalyzeFailed, vm.state.value)
        }

    @Test
    fun encodeFailure_stillNavigatesWithNullAudioPath() =
        runTest {
            val failingRenderer =
                object : WaveformRendererApi {
                    override suspend fun renderWaveformPng(
                        pcm: ShortArray,
                        outPath: String,
                    ) = outPath

                    override suspend fun encodeOpus(
                        pcm: ShortArray,
                        outPath: String,
                    ): String? = throw RuntimeException("disk full")
                }
            val classifier = ScriptedClassifier(confidencesPerCall = listOf(0.45f))
            val recorder = FakeStreamingRecorder()
            val vm =
                AudioScanViewModel(
                    classifierProvider = { Pair(classifier, AudioClassifierMode.REAL) },
                    recorder = recorder,
                    waveformRenderer = failingRenderer,
                    audioStorageDir = { "/tmp/audio" },
                    clock = { 0L },
                    normalizer = stubNormalizer,
                    ioDispatcher = Dispatchers.Unconfined,
                    inferenceDispatcher = Dispatchers.Unconfined,
                )
            vm.onPermissionState(PermissionState.Granted)
            vm.startRecording()
            recorder.emitChunks(120)
            advanceUntilIdle()
            vm.stopRecording()
            advanceUntilIdle()

            val state = vm.state.value
            assertTrue(state is AudioScanState.NavigateToMatch, "persist-fel får inte blockera ID:t, got $state")
            val source = Json.decodeFromString<ScanSourceSerialization>(state.sourceJson)
            assertEquals(null, source.audioWavPath)
        }

    @Test
    fun cancelFromAnalyzing_returnsToIdle_andStaleTimeoutDoesNotClobber() =
        runTest {
            // Renderer som hänger för evigt -> finalize fastnar i Analyzing på PNG-steget.
            val hangingRenderer =
                object : WaveformRendererApi {
                    override suspend fun renderWaveformPng(
                        pcm: ShortArray,
                        outPath: String,
                    ): String {
                        kotlinx.coroutines.awaitCancellation()
                    }

                    override suspend fun encodeOpus(
                        pcm: ShortArray,
                        outPath: String,
                    ): String? = outPath
                }
            val recorder = FakeStreamingRecorder()
            val vm =
                AudioScanViewModel(
                    classifierProvider = { Pair(ScriptedClassifier(listOf(0.45f)), AudioClassifierMode.REAL) },
                    recorder = recorder,
                    waveformRenderer = hangingRenderer,
                    audioStorageDir = { "/tmp/audio" },
                    clock = { 0L },
                    normalizer = stubNormalizer,
                    ioDispatcher = Dispatchers.Unconfined,
                    inferenceDispatcher = Dispatchers.Unconfined,
                )
            vm.onPermissionState(PermissionState.Granted)
            vm.startRecording()
            recorder.emitChunks(120)
            advanceUntilIdle()
            vm.stopRecording()
            // OBS: ingen advanceUntilIdle här — finalize hänger i renderern -> Analyzing.
            assertTrue(vm.state.value is AudioScanState.Analyzing, "got ${vm.state.value}")

            vm.cancelRecording()
            assertEquals(AudioScanState.Idle, vm.state.value)

            // Låt virtuell tid passera 15s-timeouten: det cancellade finalize-jobbet
            // får INTE klobba Idle med Error.AnalyzeFailed i efterhand.
            advanceUntilIdle()
            assertEquals(AudioScanState.Idle, vm.state.value)
        }
}
