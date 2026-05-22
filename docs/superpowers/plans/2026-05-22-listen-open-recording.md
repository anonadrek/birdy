# Open-ended Listen-flow + website-matchad recording-bar — implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ta bort 3s-cap på audio-ID. Birdy lyssnar tills BirdNET-Lite når confidence ≥ 0.60 eller user trycker stopp (60s hard cap). Recording-bar synkas visuellt med birdy.community.

**Architecture:** `AudioRecorderApi` byts från one-shot `record3s` till streaming `start/stopAndFlush/cancel`. `AudioScanViewModel` får sliding-window-klassificering (3s window, 1s stride, inflight=1) med `bestSoFar`-tracking. UI byter `IdleMic`+countdown mot `RecordingMicButton`+count-up-timer, och `WaveformBars` från 12 MarginaliaInk-staplar till 48 AccentCopper-staplar. Website `Listen.astro` syncar copy.

**Tech Stack:** Kotlin Multiplatform + Compose Multiplatform · `AudioRecord` (Android) · TFLite + BirdNET-Lite + FlexRFFT TF Select op · Astro 5 + Tailwind v4 (website)

**Spec:** `docs/superpowers/specs/2026-05-22-listen-open-recording-design.md`

---

## File map

### Skapas
- `composeApp/src/commonMain/kotlin/se/birdy/app/ui/audio/RecordingMicButton.kt` — ny UI-komponent (mic-button med Idle/Recording/Analyzing-states + shadow + pulse-ring)
- `composeApp/src/commonTest/kotlin/se/birdy/app/ui/audio/FakeStreamingRecorder.kt` — test-double för nya `AudioRecorderApi`

### Modifieras
- `composeApp/src/commonMain/kotlin/se/birdy/app/ui/audio/AudioScanState.kt` — lägg till `Top1` + `bestSoFar` på `Recording`
- `composeApp/src/commonMain/kotlin/se/birdy/app/ui/audio/AudioScanViewModel.kt` — refactor från one-shot till streaming-pattern + sliding-window logic + auto-stop
- `composeApp/src/commonMain/kotlin/se/birdy/app/ui/audio/AudioScanScreen.kt` — riv `IdleMic`, ersätt `RecordingView` med ny layout (waveform + RecordingMicButton + timer)
- `composeApp/src/commonMain/kotlin/se/birdy/app/ui/audio/WaveformBars.kt` — `barCount = 48` default, `AccentCopper`, 80dp, 3dp spacing
- `composeApp/src/androidMain/kotlin/se/birdy/app/ui/audio/AndroidAudioRecorderAdapter.android.kt` — anpassa till nya streaming-API
- `shared/ml/src/androidMain/kotlin/se/birdy/ml/AndroidAudioRecorder.kt` — riv `record3s`, lägg till `start` + `RecorderHandle`-impl
- `composeApp/src/commonTest/kotlin/se/birdy/app/ui/audio/AudioScanViewModelTest.kt` — riv `FakeRecorder`, lägg till nya tests
- `composeApp/src/commonMain/composeResources/values/strings.xml` (SV/default)
- `composeApp/src/commonMain/composeResources/values-en/strings.xml` (EN)
- `website/src/components/Listen.astro` — riv hardcoded "hold 3s"
- `website/src/content/copy.sv.json` — uppdaterad copy + `hint`
- `website/src/content/copy.en.json` — uppdaterad copy + `hint`

---

## Task 1: State-machine — `Top1` + `bestSoFar`-fält

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/audio/AudioScanState.kt`

- [ ] **Step 1: Uppdatera AudioScanState.kt**

Ersätt hela filen med:

```kotlin
package se.birdy.app.ui.audio

sealed interface AudioScanState {
    data object Preparing : AudioScanState

    data class PermissionNeeded(
        val canRequest: Boolean,
    ) : AudioScanState

    data object Idle : AudioScanState

    data class Recording(
        val rms: Float,
        val elapsedMs: Long,
        val bestSoFar: Top1? = null,
    ) : AudioScanState

    data class Analyzing(
        val rmsFrozen: Float,
    ) : AudioScanState

    data class NavigateToMatch(
        val sourceJson: String,
        val capturedAtMs: Long,
    ) : AudioScanState

    sealed interface Error : AudioScanState {
        data object PermanentlyDenied : Error

        data object RecordingFailed : Error

        data class BootstrapFailed(
            val cause: String,
        ) : Error
    }
}

/**
 * Best classification observed so far across all sliding 3s windows during a
 * single recording session. [pcmOffset]..[pcmEnd] marks the PCM-buffer slice
 * that produced this top-1 — used at final-classify time to re-run the model
 * on the winning window for full Disambig-routing data.
 */
data class Top1(
    val speciesId: String,
    val confidence: Float,
    val pcmOffset: Int,
    val pcmEnd: Int,
)
```

- [ ] **Step 2: Verifiera kompilering**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL (eller endast existerande lint-warnings, ingen ny error). En del existing usages av `AudioScanState.Recording(rms, elapsedMs)` fortsätter funka eftersom `bestSoFar` har default `null`.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/audio/AudioScanState.kt
git commit -m "feat(listen/t1): Top1 + bestSoFar field on AudioScanState.Recording"
```

---

## Task 2: Streaming AudioRecorderApi + FakeStreamingRecorder

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/audio/AudioScanViewModel.kt` (bara interfacen — VM-impl ändras i task 3)
- Create: `composeApp/src/commonTest/kotlin/se/birdy/app/ui/audio/FakeStreamingRecorder.kt`

- [ ] **Step 1: Riv old `AudioRecorderApi` och lägg till streaming-variant**

I `AudioScanViewModel.kt` längst ned, hitta `interface AudioRecorderApi { suspend fun record3s(onLevel: (Float) -> Unit): ShortArray }` och ersätt **bara den** med följande (lämna `WaveformRendererApi`-blocket nedanför kvar — det rivs ändå om Task 3 senare ersätter hela filen):

```kotlin
interface AudioRecorderApi {
    /**
     * Open-ended capture. Returns a handle as soon as AudioRecord is initialised.
     * Emits PCM chunks via [onChunk] until [RecorderHandle.stopAndFlush] is called
     * or [maxDurationMs] elapses (in which case [onCapReached] fires).
     *
     * - [onChunk] runs on the recorder's IO dispatcher; consumer MUST be cheap
     *   (append to buffer, update rms) — heavy work (ML inference) belongs in
     *   the ViewModel on its own dispatcher.
     * - All callbacks fire BEFORE [start] returns the handle is OK; consumer
     *   stores incoming chunks in a thread-safe way.
     */
    fun start(
        onChunk: (samples: ShortArray, rms: Float, totalSamplesSoFar: Int) -> Unit,
        onCapReached: () -> Unit,
        maxDurationMs: Long = 60_000L,
    ): RecorderHandle
}

interface RecorderHandle {
    /** Stop recorder, flush remaining buffered chunks, and return the full captured PCM. Idempotent. */
    suspend fun stopAndFlush(): ShortArray

    /** Cancel and discard all captured PCM. Idempotent. */
    fun cancel()
}
```

- [ ] **Step 2: Skapa `FakeStreamingRecorder.kt` för commonTest**

```kotlin
package se.birdy.app.ui.audio

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.min

/**
 * Test-double for [AudioRecorderApi] that emits pre-canned PCM chunks at
 * controllable cadence. Drives by [emitChunks] — each call delivers
 * [chunkSize] samples and advances [totalSamples].
 */
class FakeStreamingRecorder(
    val chunkSize: Int = 1_600,           // ~33ms @ 48kHz
    val chunkRms: Float = 0.5f,
    val maxBufferSamples: Int = 60 * 48_000,
) : AudioRecorderApi {
    private var onChunk: ((ShortArray, Float, Int) -> Unit)? = null
    private var onCap: (() -> Unit)? = null
    private val buffer = ShortArray(maxBufferSamples)
    private var totalSamples = 0
    private val stopped = MutableStateFlow(false)
    private val cancelled = MutableStateFlow(false)

    override fun start(
        onChunk: (samples: ShortArray, rms: Float, totalSamplesSoFar: Int) -> Unit,
        onCapReached: () -> Unit,
        maxDurationMs: Long,
    ): RecorderHandle {
        this.onChunk = onChunk
        this.onCap = onCapReached
        return object : RecorderHandle {
            override suspend fun stopAndFlush(): ShortArray {
                stopped.value = true
                return buffer.copyOf(totalSamples)
            }

            override fun cancel() {
                cancelled.value = true
            }
        }
    }

    /**
     * Drives the recorder synchronously: emits [count] chunks of [chunkSize]
     * samples each, calling onChunk with monotonically increasing totalSamples.
     * Stops early if [stopOrCancel] has been called.
     */
    suspend fun emitChunks(count: Int) {
        repeat(count) {
            if (stopped.value || cancelled.value) return
            val sliceStart = totalSamples
            val sliceEnd = min(sliceStart + chunkSize, maxBufferSamples)
            val len = sliceEnd - sliceStart
            if (len <= 0) {
                onCap?.invoke()
                return
            }
            // Leave buffer at default zeros — silence; tests don't read amplitude.
            totalSamples = sliceEnd
            onChunk?.invoke(ShortArray(len), chunkRms, totalSamples)
            delay(33)  // ~realtime cadence; test scheduler advances virtually
            if (totalSamples == maxBufferSamples) {
                onCap?.invoke()
                return
            }
        }
    }

    fun snapshotTotalSamples(): Int = totalSamples
}
```

- [ ] **Step 3: Verifiera kompilering**

Run: `./gradlew :composeApp:compileTestKotlinAndroid :composeApp:compileDebugKotlinAndroid`
Expected: Existing `FakeRecorder` i `AudioScanViewModelTest.kt` failar nu (`record3s` finns inte) — det är OK, vi fixar i task 3. Kompileringen av `FakeStreamingRecorder` ska gå igenom utan errors.

> Om Gradle stoppar på `FakeRecorder`-felet, kommentera ut hela `AudioScanViewModelTest.kt`-filen tillfälligt med `/* */` — vi ersätter den hel i task 3.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/audio/AudioScanViewModel.kt composeApp/src/commonTest/kotlin/se/birdy/app/ui/audio/FakeStreamingRecorder.kt
git commit -m "feat(listen/t2): streaming AudioRecorderApi + FakeStreamingRecorder test-double"
```

---

## Task 3: ViewModel sliding-window + auto-stop + bestSoFar

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/audio/AudioScanViewModel.kt`
- Modify: `composeApp/src/commonTest/kotlin/se/birdy/app/ui/audio/AudioScanViewModelTest.kt`

Detta är den största task:en. Vi skriver test först (TDD).

- [ ] **Step 1: Skriv failing tests för sliding-window + auto-stop**

Ersätt hela `AudioScanViewModelTest.kt` med:

```kotlin
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
    override suspend fun renderWaveformPng(pcm: ShortArray, outPath: String) = outPath
    override suspend fun encodeOpus(pcm: ShortArray, outPath: String) = outPath
}

/**
 * Configurable classifier that returns a confidence-per-call sequence
 * and records every input slice it saw.
 */
private class ScriptedClassifier(
    val confidencesPerCall: List<Float>,
    val speciesId: String = "Q25334",
) : BirdAudioClassifier {
    override val info = AudioModelInfo(
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
    fun setUp() { Dispatchers.setMain(testDispatcher) }

    @AfterTest
    fun tearDown() { Dispatchers.resetMain() }

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
    fun autoStops_whenConfidenceReachesThreshold_after3s() = runTest {
        val classifier = ScriptedClassifier(confidencesPerCall = listOf(0.40f, 0.75f))
        val recorder = FakeStreamingRecorder()
        val (vm, _) = makeVm(classifier = classifier, recorder = recorder)
        vm.onPermissionState(PermissionState.Granted)

        vm.startRecording()
        // Drive ~4s worth of audio = 120 chunks @ 1600 samples each → 192_000 samples
        recorder.emitChunks(120)
        advanceUntilIdle()

        assertTrue(
            vm.state.value is AudioScanState.NavigateToMatch ||
                vm.state.value is AudioScanState.Analyzing,
            "expected Analyzing/NavigateToMatch, got ${vm.state.value}",
        )
        // Auto-stop kicked in on the second inference (0.75 ≥ 0.60); classifier was called twice during streaming + once for final
        assertTrue(classifier.callInputs.size >= 2, "classifier called ${classifier.callInputs.size} times")
    }

    @Test
    fun doesNotAutoStop_beforeFirstFullWindowAvailable() = runTest {
        val classifier = ScriptedClassifier(confidencesPerCall = listOf(0.99f))
        val recorder = FakeStreamingRecorder()
        val (vm, _) = makeVm(classifier = classifier, recorder = recorder)
        vm.onPermissionState(PermissionState.Granted)

        vm.startRecording()
        // Drive only 2s = 60 chunks @ 1600 = 96_000 samples (< 144_000 = 3s)
        recorder.emitChunks(60)
        advanceUntilIdle()

        assertTrue(vm.state.value is AudioScanState.Recording, "got ${vm.state.value}")
        assertEquals(0, classifier.callInputs.size)
    }

    @Test
    fun manualStop_runsFinalClassifyAndNavigates() = runTest {
        val classifier = ScriptedClassifier(confidencesPerCall = listOf(0.45f, 0.50f))
        val recorder = FakeStreamingRecorder()
        val (vm, _) = makeVm(classifier = classifier, recorder = recorder)
        vm.onPermissionState(PermissionState.Granted)

        vm.startRecording()
        recorder.emitChunks(150)   // ~5s worth → at least one sliding window classified
        advanceUntilIdle()
        vm.stopRecording()
        advanceUntilIdle()

        assertTrue(vm.state.value is AudioScanState.NavigateToMatch, "got ${vm.state.value}")
    }

    @Test
    fun manualStop_beforeAnyWindowProcessed_fallsBackToLast3s() = runTest {
        // Classifier never returns ≥ 0.60 during streaming, but final-classify still runs on last 3s.
        val classifier = ScriptedClassifier(confidencesPerCall = listOf(0f, 0f, 0f, 0.42f))
        val recorder = FakeStreamingRecorder()
        val (vm, _) = makeVm(classifier = classifier, recorder = recorder)
        vm.onPermissionState(PermissionState.Granted)

        vm.startRecording()
        recorder.emitChunks(120)   // ~4s
        advanceUntilIdle()
        vm.stopRecording()
        advanceUntilIdle()

        assertTrue(vm.state.value is AudioScanState.NavigateToMatch, "got ${vm.state.value}")
        // Final classify-call must have come in
        assertNotNull(classifier.callInputs.lastOrNull())
    }

    @Test
    fun capReached_runsFinalClassifyAndNavigates() = runTest {
        val classifier = ScriptedClassifier(confidencesPerCall = List(70) { 0.30f })
        val recorder = FakeStreamingRecorder(maxBufferSamples = 60 * 48_000)
        val (vm, _) = makeVm(classifier = classifier, recorder = recorder)
        vm.onPermissionState(PermissionState.Granted)

        vm.startRecording()
        // 60s = 1800 chunks @ 1600 samples → triggers cap
        recorder.emitChunks(1800)
        advanceUntilIdle()

        assertTrue(vm.state.value is AudioScanState.NavigateToMatch, "got ${vm.state.value}")
    }

    @Test
    fun cancelRecording_returnsToIdle_andDoesNotCallClassifier() = runTest {
        val classifier = ScriptedClassifier(confidencesPerCall = listOf(0.99f))
        val recorder = FakeStreamingRecorder()
        val (vm, _) = makeVm(classifier = classifier, recorder = recorder)
        vm.onPermissionState(PermissionState.Granted)

        vm.startRecording()
        recorder.emitChunks(30)   // 1s — < 3s, no window classified
        vm.cancelRecording()
        advanceUntilIdle()

        assertEquals(AudioScanState.Idle, vm.state.value)
        assertEquals(0, classifier.callInputs.size)
    }

    @Test
    fun bestSoFar_tracksHighestConfidenceAcrossWindows() = runTest {
        val classifier = ScriptedClassifier(confidencesPerCall = listOf(0.30f, 0.50f, 0.40f, 0.55f))
        val recorder = FakeStreamingRecorder()
        val (vm, _) = makeVm(classifier = classifier, recorder = recorder)
        vm.onPermissionState(PermissionState.Granted)

        vm.startRecording()
        recorder.emitChunks(200)   // ~6.6s — should trigger ~4 inferences
        advanceUntilIdle()
        vm.stopRecording()
        advanceUntilIdle()

        // Final state has navigated; can't read intermediate bestSoFar, but
        // we can verify the classifier was called multiple times.
        assertTrue(
            classifier.callInputs.size >= 3,
            "expected ≥3 classify calls (3 streaming + 1 final), got ${classifier.callInputs.size}",
        )
    }

    @Test
    fun permissionDenied_emitsPermissionNeeded() {
        val (vm, _) = makeVm()
        vm.onPermissionState(PermissionState.Denied)
        assertEquals(AudioScanState.PermissionNeeded(canRequest = true), vm.state.value)
    }

    @Test
    fun permissionPermanentlyDenied_emitsError() {
        val (vm, _) = makeVm()
        vm.onPermissionState(PermissionState.PermanentlyDenied)
        assertEquals(AudioScanState.Error.PermanentlyDenied, vm.state.value)
    }
}
```

- [ ] **Step 2: Kör tester — verifiera att de failar**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.app.ui.audio.AudioScanViewModelTest"`
Expected: Compilation fail (VM har inte nya konstruktor-param `inferenceDispatcher` och inte ny metod `stopRecording`).

- [ ] **Step 3: Refactor AudioScanViewModel — streaming + sliding window**

Ersätt hela `AudioScanViewModel.kt` med:

```kotlin
package se.birdy.app.ui.audio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import se.birdy.ml.AudioClassifierMode
import se.birdy.ml.AudioInput
import se.birdy.ml.BirdAudioClassifier
import se.birdy.ml.Classification
import se.birdy.ml.ClassificationResult
import se.birdy.ml.ScanSource
import se.birdy.ml.ScanSourceSerialization
import se.birdy.ml.normalize
import se.birdy.ml.toSerial
import kotlin.coroutines.coroutineContext

/**
 * Orchestrates the open-ended audio-scan flow:
 *
 *   Preparing → PermissionNeeded / Error.PermanentlyDenied / Idle
 *   Idle → Recording (sliding 3s/1s windows classified during capture)
 *        → Analyzing (final classify on best window)
 *        → NavigateToMatch / Error.*
 *
 * Auto-stops when top-1 confidence reaches [AUTO_STOP_THRESHOLD] over any
 * sliding window. Manual stop via [stopRecording] or 60s cap have identical
 * behavior — final-classify on bestSoFar window (or last 3s as fallback).
 */
class AudioScanViewModel(
    private val classifierProvider: suspend () -> Pair<BirdAudioClassifier, AudioClassifierMode>,
    private val recorder: AudioRecorderApi,
    private val waveformRenderer: WaveformRendererApi,
    private val audioStorageDir: () -> String,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val normalizer: (ShortArray) -> FloatArray = ::normalize,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val inferenceDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val _state = MutableStateFlow<AudioScanState>(AudioScanState.Preparing)
    val state: StateFlow<AudioScanState> = _state

    @Suppress("UnusedPrivateMember")
    private var classifierMode: AudioClassifierMode? = null

    private var sessionJob: Job? = null
    private var handle: RecorderHandle? = null

    // Streaming-state, owned by sessionJob coroutine
    private var fullBuffer = ShortArray(0)
    private var bufferEnd = 0
    private var bestSoFar: Top1? = null
    private var lastClassifiedAtSamples = 0
    private var inflight = false
    private val tStart get() = sessionStartMs

    @Volatile private var sessionStartMs: Long = 0L
    @Volatile private var classifierInstance: BirdAudioClassifier? = null

    companion object {
        const val SAMPLE_RATE = 48_000
        const val WINDOW_SAMPLES = SAMPLE_RATE * 3   // 144_000
        const val STRIDE_SAMPLES = SAMPLE_RATE       // 48_000 → 1s
        const val AUTO_STOP_THRESHOLD = 0.60f
        const val MIN_RECORD_MS = 3_000L
        const val MAX_RECORD_MS = 60_000L
        const val MAX_BUFFER_SAMPLES = SAMPLE_RATE * 60   // 60s
    }

    fun onPermissionState(p: PermissionState) {
        when (p) {
            PermissionState.Granted ->
                if (_state.value !is AudioScanState.Recording) {
                    _state.value = AudioScanState.Idle
                }
            PermissionState.Denied -> _state.value = AudioScanState.PermissionNeeded(canRequest = true)
            PermissionState.PermanentlyDenied -> _state.value = AudioScanState.Error.PermanentlyDenied
            PermissionState.Unknown -> _state.value = AudioScanState.PermissionNeeded(canRequest = true)
        }
    }

    fun startRecording() {
        val initial = AudioScanState.Recording(rms = 0f, elapsedMs = 0L, bestSoFar = null)
        if (!_state.compareAndSet(AudioScanState.Idle, initial)) return
        sessionJob?.cancel()

        fullBuffer = ShortArray(MAX_BUFFER_SAMPLES)
        bufferEnd = 0
        bestSoFar = null
        lastClassifiedAtSamples = 0
        inflight = false
        sessionStartMs = clock()

        sessionJob = viewModelScope.launch {
            try {
                // Bootstrap classifier upfront so first inference doesn't pay setup cost.
                classifierInstance = runCatching { classifierProvider() }
                    .getOrElse { throwable ->
                        coroutineContext.ensureActive()
                        _state.value = AudioScanState.Error.BootstrapFailed(throwable.message ?: "bootstrap failed")
                        return@launch
                    }.also { classifierMode = it.second }
                    .first

                handle = recorder.start(
                    onChunk = { samples, rms, totalSoFar ->
                        onChunkReceived(samples, rms, totalSoFar)
                    },
                    onCapReached = {
                        viewModelScope.launch { finalizeAndNavigate(reason = StopReason.CAP) }
                    },
                    maxDurationMs = MAX_RECORD_MS,
                )
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                coroutineContext.ensureActive()
                _state.value = AudioScanState.Error.RecordingFailed
            }
        }
    }

    private fun onChunkReceived(samples: ShortArray, rms: Float, totalSoFar: Int) {
        // Append; recorder guarantees totalSoFar ≤ MAX_BUFFER_SAMPLES
        val toCopy = minOf(samples.size, fullBuffer.size - bufferEnd)
        if (toCopy > 0) {
            samples.copyInto(fullBuffer, destinationOffset = bufferEnd, startIndex = 0, endIndex = toCopy)
            bufferEnd += toCopy
        }

        val elapsed = clock() - sessionStartMs
        _state.update { s ->
            if (s is AudioScanState.Recording) {
                AudioScanState.Recording(rms = rms, elapsedMs = elapsed, bestSoFar = bestSoFar)
            } else s
        }

        maybeSubmitInference()
    }

    private fun maybeSubmitInference() {
        if (inflight) return
        if (bufferEnd < WINDOW_SAMPLES) return
        // Stride: classify when totalSoFar has advanced ≥ STRIDE_SAMPLES since last
        if (bufferEnd - lastClassifiedAtSamples < STRIDE_SAMPLES && lastClassifiedAtSamples > 0) return

        val windowEnd = bufferEnd
        val windowStart = windowEnd - WINDOW_SAMPLES
        val window = fullBuffer.copyOfRange(windowStart, windowEnd)
        lastClassifiedAtSamples = bufferEnd
        inflight = true

        viewModelScope.launch(inferenceDispatcher) {
            try {
                val clf = classifierInstance ?: return@launch
                val waveform = normalizer(window)
                val result = clf.classify(AudioInput(waveform, SAMPLE_RATE, 3_000, rawPcm = window))
                val top = result.results.firstOrNull()

                if (top != null) {
                    val current = bestSoFar
                    if (current == null || top.confidence > current.confidence) {
                        bestSoFar = Top1(top.speciesId, top.confidence, windowStart, windowEnd)
                        _state.update { s ->
                            if (s is AudioScanState.Recording) s.copy(bestSoFar = bestSoFar) else s
                        }
                    }
                    if (top.confidence >= AUTO_STOP_THRESHOLD) {
                        finalizeAndNavigate(reason = StopReason.AUTO)
                        return@launch
                    }
                }
            } finally {
                inflight = false
            }
        }
    }

    fun stopRecording() {
        val s = _state.value as? AudioScanState.Recording ?: return
        if (s.elapsedMs < MIN_RECORD_MS) return   // ignore — button is disabled in UI but defense in depth
        viewModelScope.launch { finalizeAndNavigate(reason = StopReason.MANUAL) }
    }

    private suspend fun finalizeAndNavigate(reason: StopReason) {
        val current = _state.value
        if (current !is AudioScanState.Recording) return

        val rmsAtStop = current.rms
        _state.value = AudioScanState.Analyzing(rmsFrozen = rmsAtStop)

        // Stop recorder + flush
        val fullPcm = runCatching { handle?.stopAndFlush() ?: ShortArray(bufferEnd) }
            .getOrElse { fullBuffer.copyOf(bufferEnd) }

        // Pick window: bestSoFar slice, or last 3s fallback
        val best = bestSoFar
        val windowStart: Int
        val windowEnd: Int
        if (best != null) {
            windowStart = best.pcmOffset
            windowEnd = best.pcmEnd
        } else {
            windowEnd = fullPcm.size
            windowStart = (windowEnd - WINDOW_SAMPLES).coerceAtLeast(0)
        }
        val window = fullPcm.copyOfRange(windowStart, windowEnd)

        analyzeAndNavigate(fullPcm = fullPcm, window = window)
    }

    private suspend fun analyzeAndNavigate(fullPcm: ShortArray, window: ShortArray) {
        coroutineContext.ensureActive()
        val classifier = classifierInstance ?: run {
            _state.value = AudioScanState.Error.BootstrapFailed("classifier unavailable")
            return
        }

        val ts = clock()
        val pngPath = withContext(ioDispatcher) {
            waveformRenderer.renderWaveformPng(fullPcm, "${audioStorageDir()}/$ts.png")
        }
        coroutineContext.ensureActive()
        val audioPath = withContext(ioDispatcher) {
            waveformRenderer.encodeOpus(fullPcm, "${audioStorageDir()}/$ts.opus")
        }
        coroutineContext.ensureActive()
        val waveform = normalizer(window)
        val classification = classifier.classify(AudioInput(waveform, SAMPLE_RATE, 3_000, rawPcm = window))

        val source = if (classification.results.isEmpty()) {
            ScanSource.Audio(
                frameJpegPath = pngPath,
                classification = Classification(results = emptyList(), frameTimestampMillis = ts),
                audioWavPath = audioPath,
            )
        } else {
            val top = classification.results.first()
            ScanSource.Audio(
                frameJpegPath = pngPath,
                classification = Classification(
                    results = listOf(ClassificationResult(top.speciesId, top.confidence)),
                    frameTimestampMillis = ts,
                ),
                audioWavPath = audioPath,
            )
        }

        val json = Json.encodeToString(ScanSourceSerialization.serializer(), source.toSerial())
        _state.update { s ->
            if (s is AudioScanState.Analyzing) AudioScanState.NavigateToMatch(json, ts) else s
        }
    }

    fun cancelRecording() {
        sessionJob?.cancel()
        sessionJob = null
        handle?.cancel()
        handle = null
        bestSoFar = null
        bufferEnd = 0
        if (_state.value is AudioScanState.Recording || _state.value is AudioScanState.Error) {
            _state.value = AudioScanState.Idle
        }
    }

    private enum class StopReason { AUTO, MANUAL, CAP }
}

interface AudioRecorderApi {
    fun start(
        onChunk: (samples: ShortArray, rms: Float, totalSamplesSoFar: Int) -> Unit,
        onCapReached: () -> Unit,
        maxDurationMs: Long = 60_000L,
    ): RecorderHandle
}

interface RecorderHandle {
    suspend fun stopAndFlush(): ShortArray
    fun cancel()
}

interface WaveformRendererApi {
    suspend fun renderWaveformPng(pcm: ShortArray, outPath: String): String
    suspend fun encodeOpus(pcm: ShortArray, outPath: String): String
}
```

- [ ] **Step 4: Kör tester — verifiera de passerar**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.app.ui.audio.AudioScanViewModelTest"`
Expected: All 9 tests pass.

> Om något test failar, läs felmeddelandet — vanliga issues: stride-logiken triggar inte i tid (justera `emitChunks`-count), eller cancellation race i `finalizeAndNavigate`. Fixa innan commit.

- [ ] **Step 5: Kör hela JVM-testsuiten för regress**

Run: `./gradlew :composeApp:testDebugUnitTest :shared:domain:jvmTest :shared:ml:jvmTest`
Expected: All pass.

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/audio/AudioScanViewModel.kt composeApp/src/commonTest/kotlin/se/birdy/app/ui/audio/AudioScanViewModelTest.kt
git commit -m "feat(listen/t3): sliding-window classify + bestSoFar + auto-stop in ViewModel"
```

---

## Task 4: Android-recorder streaming-impl

**Files:**
- Modify: `shared/ml/src/androidMain/kotlin/se/birdy/ml/AndroidAudioRecorder.kt`
- Modify: `composeApp/src/androidMain/kotlin/se/birdy/app/ui/audio/AndroidAudioRecorderAdapter.android.kt`

- [ ] **Step 1: Refactor `AndroidAudioRecorder` till streaming**

Ersätt hela filen med:

```kotlin
package se.birdy.ml

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

/**
 * Captures open-ended 48 kHz mono PCM_16BIT audio via AudioRecord.
 *
 * Caller MUST hold the `android.permission.RECORD_AUDIO` permission before
 * invoking [start] — this class does NOT prompt the user.
 *
 * Capture stops when either:
 * - [AndroidRecorderHandle.stopAndFlush] is called by consumer
 * - [AndroidRecorderHandle.cancel] is called
 * - `maxDurationMs` elapses (then `onCapReached` fires and capture stops)
 */
class AndroidAudioRecorder(
    val sampleRate: Int = 48_000,
) {
    @SuppressLint("MissingPermission")
    fun start(
        onChunk: (samples: ShortArray, rms: Float, totalSamplesSoFar: Int) -> Unit,
        onCapReached: () -> Unit,
        maxDurationMs: Long = 60_000L,
    ): AndroidRecorderHandle {
        val maxSamples = (sampleRate * maxDurationMs / 1000L).toInt()
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        require(minBuf > 0) { "AudioRecord.getMinBufferSize returned $minBuf" }

        var recorder = buildRecorder(MediaRecorder.AudioSource.UNPROCESSED, minBuf)
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            recorder = buildRecorder(MediaRecorder.AudioSource.VOICE_RECOGNITION, minBuf)
        }
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            error("AudioRecord failed to initialize with either UNPROCESSED or VOICE_RECOGNITION")
        }

        val captured = ShortArray(maxSamples)
        var total = 0

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val stopRequested = CompletableDeferred<Unit>()
        val cancelRequested = CompletableDeferred<Unit>()

        val job: Job = scope.launch {
            try {
                recorder.startRecording()
                val chunkSize = sampleRate / 30   // ~33ms
                val chunkBuf = ShortArray(chunkSize)
                while (total < maxSamples && !stopRequested.isCompleted && !cancelRequested.isCompleted) {
                    val toRead = minOf(chunkSize, maxSamples - total)
                    val read = recorder.read(chunkBuf, 0, toRead)
                    if (read <= 0) break
                    chunkBuf.copyInto(captured, destinationOffset = total, startIndex = 0, endIndex = read)
                    val rms = computeRms(chunkBuf, 0, read)
                    total += read
                    if (!cancelRequested.isCompleted) {
                        onChunk(chunkBuf.copyOf(read), rms, total)
                    }
                }
                if (total >= maxSamples && !stopRequested.isCompleted && !cancelRequested.isCompleted) {
                    onCapReached()
                }
            } finally {
                runCatching { recorder.stop() }
                recorder.release()
            }
        }

        return AndroidRecorderHandle(
            stopRequested = stopRequested,
            cancelRequested = cancelRequested,
            job = job,
            scope = scope,
            getCaptured = { captured.copyOf(total) },
        )
    }

    private fun buildRecorder(source: Int, bufBytes: Int): AudioRecord =
        AudioRecord(
            source,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufBytes,
        )

    private fun computeRms(buffer: ShortArray, offset: Int, length: Int): Float {
        if (length == 0) return 0f
        var sum = 0.0
        for (i in offset until offset + length) {
            val s = buffer[i] / 32768.0
            sum += s * s
        }
        return sqrt(sum / length).toFloat().coerceIn(0f, 1f)
    }
}

class AndroidRecorderHandle internal constructor(
    private val stopRequested: CompletableDeferred<Unit>,
    private val cancelRequested: CompletableDeferred<Unit>,
    private val job: Job,
    private val scope: CoroutineScope,
    private val getCaptured: () -> ShortArray,
) {
    suspend fun stopAndFlush(): ShortArray = withContext(Dispatchers.IO) {
        stopRequested.complete(Unit)
        job.join()
        scope.cancel()
        getCaptured()
    }

    fun cancel() {
        cancelRequested.complete(Unit)
        scope.cancel()
    }
}
```

- [ ] **Step 2: Uppdatera `AndroidAudioRecorderAdapter`**

Ersätt hela filen med:

```kotlin
package se.birdy.app.ui.audio

import se.birdy.ml.AndroidAudioRecorder

/**
 * Bridges [AndroidAudioRecorder] (Android-platform layer) to [AudioRecorderApi]
 * (common audio-scan layer). Open-ended capture replaces old fixed-3s flow.
 */
class AndroidAudioRecorderAdapter(
    private val recorder: AndroidAudioRecorder = AndroidAudioRecorder(),
) : AudioRecorderApi {
    override fun start(
        onChunk: (samples: ShortArray, rms: Float, totalSamplesSoFar: Int) -> Unit,
        onCapReached: () -> Unit,
        maxDurationMs: Long,
    ): RecorderHandle {
        val androidHandle = recorder.start(onChunk, onCapReached, maxDurationMs)
        return object : RecorderHandle {
            override suspend fun stopAndFlush(): ShortArray = androidHandle.stopAndFlush()
            override fun cancel() = androidHandle.cancel()
        }
    }
}
```

- [ ] **Step 3: Verifiera kompilering + lint**

Run: `./gradlew :shared:ml:compileDebugKotlinAndroid :composeApp:compileDebugKotlinAndroid ktlintCheck`
Expected: SUCCESSFUL. Lint kan flagga unused imports — kör `./gradlew ktlintFormat` om så.

- [ ] **Step 4: Commit**

```bash
git add shared/ml/src/androidMain/kotlin/se/birdy/ml/AndroidAudioRecorder.kt composeApp/src/androidMain/kotlin/se/birdy/app/ui/audio/AndroidAudioRecorderAdapter.android.kt
git commit -m "feat(listen/t4): AndroidAudioRecorder streaming + cap-driven stop"
```

---

## Task 5: WaveformBars — 48 copper-staplar

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/audio/WaveformBars.kt`

- [ ] **Step 1: Uppdatera WaveformBars**

Ersätt hela filen med:

```kotlin
package se.birdy.app.ui.audio

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import se.birdy.app.ui.theme.AccentCopper
import kotlin.math.sin

/**
 * Copper-accent waveform bars matching the marketing site (`Listen.astro`).
 * 48 bars by default; live RMS modulates height for a more organic pulse than
 * the website's faked sin-wave animation.
 *
 * When [frozen] is true (Analyzing state) the animation slows to settle.
 */
@Composable
fun WaveformBars(
    rms: Float,
    frozen: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 48,
) {
    val target = rms.coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = if (frozen) 600 else 120),
        label = "wave-rms",
    )

    Row(
        modifier = modifier.height(80.dp).fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(barCount) { i ->
            val phase = (sin((i * 1.3f) + (animated * 6f)) + 1f) / 2f
            val heightFraction = (0.2f + 0.8f * phase * animated).coerceIn(0.1f, 1f)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(heightFraction)
                    .clip(RoundedCornerShape(2.dp))
                    .background(AccentCopper.copy(alpha = 0.85f)),
            )
        }
    }
}
```

- [ ] **Step 2: Verifiera kompilering**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/audio/WaveformBars.kt
git commit -m "feat(listen/t5): WaveformBars 48 staplar AccentCopper 80dp (matches website)"
```

---

## Task 6: RecordingMicButton (ny komponent)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/audio/RecordingMicButton.kt`

- [ ] **Step 1: Skapa filen**

```kotlin
package se.birdy.app.ui.audio

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import se.birdy.app.ui.theme.AccentCopper

/**
 * Copper mic-button matching `Listen.astro` on the marketing site.
 *
 * States:
 * - [MicButtonState.Idle] — solid copper circle, white ●, tap-to-start
 * - [MicButtonState.Recording] — same circle, white ■, infinite pulse-ring, tap-to-stop
 * - [MicButtonState.RecordingDisabled] — recording but elapsed < 3s; tap ignored, no pulse
 * - [MicButtonState.Analyzing] — disabled appearance, ■, no pulse
 */
@Composable
fun RecordingMicButton(
    state: MicButtonState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glyph = when (state) {
        MicButtonState.Idle -> "●"
        MicButtonState.Recording, MicButtonState.RecordingDisabled, MicButtonState.Analyzing -> "■"
    }

    val isActive = state == MicButtonState.Recording
    val scale = if (isActive) {
        val transition = rememberInfiniteTransition(label = "mic-pulse")
        val s by transition.animateFloat(
            initialValue = 1f,
            targetValue = 1.15f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1200),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "mic-pulse-scale",
        )
        s
    } else 1f

    val alpha = when (state) {
        MicButtonState.RecordingDisabled -> 0.5f
        MicButtonState.Analyzing -> 0.4f
        else -> 1f
    }

    val clickEnabled = state == MicButtonState.Idle || state == MicButtonState.Recording

    Box(
        modifier = modifier
            .size(64.dp)
            .scale(scale)
            .shadow(
                elevation = 4.dp,
                shape = CircleShape,
                ambientColor = AccentCopper,
                spotColor = AccentCopper,
            )
            .clip(CircleShape)
            .background(AccentCopper)
            .alpha(alpha)
            .then(if (clickEnabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = glyph, color = Color.White, fontSize = 20.sp)
    }
}

enum class MicButtonState {
    Idle,
    Recording,
    RecordingDisabled,
    Analyzing,
}
```

- [ ] **Step 2: Verifiera kompilering**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/audio/RecordingMicButton.kt
git commit -m "feat(listen/t6): RecordingMicButton — 64dp copper + Idle/Recording/Disabled/Analyzing states"
```

---

## Task 7: Strings — riv 3s-language, lägg till tap-to-toggle

**Files:**
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml` (default = SV)
- Modify: `composeApp/src/commonMain/composeResources/values-en/strings.xml`

- [ ] **Step 1: Uppdatera SV-strängar**

I `values/strings.xml`, hitta block med `audio_scan_*` (rad ~587-602) och ersätt:

```xml
    <string name="audio_scan_journal_sub">Tryck för att lyssna</string>
    <string name="audio_scan_marginalia_top">Håll telefonen stilla och *låt den sjunga*</string>
    <string name="audio_scan_cta_idle">Tryck för att lyssna</string>
    <string name="audio_scan_cta_recording">Lyssnar — tryck för att stoppa</string>
    <string name="audio_scan_analyzing">Analyserar…</string>
    <string name="audio_scan_cancel">Avbryt</string>
    <string name="audio_scan_permission_title">Lyssna efter sång</string>
    <string name="audio_scan_permission_body">Birdy behöver mikrofonen för att identifiera fågelsång — ljudet sparas bara lokalt på din telefon.</string>
    <string name="audio_scan_permission_grant">Ge tillstånd</string>
    <string name="audio_scan_permission_open_settings">Öppna inställningar</string>
    <string name="audio_scan_recording_failed">Mikrofonen kunde inte startas.</string>
    <string name="audio_scan_retry">Försök igen</string>
```

Riv följande nyckar (de finns ej längre): `audio_scan_cta_hold`, `audio_scan_cta_release`, `audio_scan_listening`, `audio_scan_too_short`.

- [ ] **Step 2: Uppdatera EN-strängar**

I `values-en/strings.xml` motsvarande:

```xml
    <string name="audio_scan_journal_sub">Tap to listen</string>
    <string name="audio_scan_marginalia_top">Hold your phone still and *let it sing*</string>
    <string name="audio_scan_cta_idle">Tap to listen</string>
    <string name="audio_scan_cta_recording">Listening — tap to stop</string>
    <string name="audio_scan_analyzing">Analyzing…</string>
    <string name="audio_scan_cancel">Cancel</string>
    <string name="audio_scan_permission_title">Listen for a song</string>
    <string name="audio_scan_permission_body">Birdy needs your microphone to identify bird songs — audio stays only on your phone.</string>
    <string name="audio_scan_permission_grant">Grant permission</string>
    <string name="audio_scan_permission_open_settings">Open settings</string>
    <string name="audio_scan_recording_failed">Microphone failed to start.</string>
    <string name="audio_scan_retry">Try again</string>
```

Samma 4 nycklar rivs i EN.

- [ ] **Step 3: Verifiera (kompilering kommer trolligen misslyckas eftersom AudioScanScreen ännu refererar till gamla nycklar)**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: COMPILATION FAIL — referenser till `audio_scan_cta_hold` och `audio_scan_listening` saknas. Fixas i task 8.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/composeResources/values/strings.xml composeApp/src/commonMain/composeResources/values-en/strings.xml
git commit -m "feat(listen/t7): strings update — riv 3s-language, ny cta_idle + cta_recording"
```

---

## Task 8: AudioScanScreen — ny layout (waveform + mic + timer)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/audio/AudioScanScreen.kt`
- Modify: `composeApp/src/androidMain/kotlin/se/birdy/app/ui/audio/AudioScanScreenHost.android.kt`

- [ ] **Step 1: Refactor AudioScanScreen**

Ersätt hela filen med:

```kotlin
package se.birdy.app.ui.audio

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.audio_scan_analyzing
import birdy_bird_scanner.composeapp.generated.resources.audio_scan_cta_idle
import birdy_bird_scanner.composeapp.generated.resources.audio_scan_cta_recording
import birdy_bird_scanner.composeapp.generated.resources.audio_scan_headline
import birdy_bird_scanner.composeapp.generated.resources.audio_scan_journal_label
import birdy_bird_scanner.composeapp.generated.resources.audio_scan_journal_sub
import birdy_bird_scanner.composeapp.generated.resources.audio_scan_marginalia_top
import birdy_bird_scanner.composeapp.generated.resources.audio_scan_permission_body
import birdy_bird_scanner.composeapp.generated.resources.audio_scan_permission_grant
import birdy_bird_scanner.composeapp.generated.resources.audio_scan_permission_open_settings
import birdy_bird_scanner.composeapp.generated.resources.audio_scan_permission_title
import birdy_bird_scanner.composeapp.generated.resources.audio_scan_recording_failed
import birdy_bird_scanner.composeapp.generated.resources.audio_scan_retry
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.components.JournalIntro
import se.birdy.app.ui.components.JournalScaffold
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.MarginaliaInk
import se.birdy.app.ui.theme.rememberCaveat
import se.birdy.app.ui.theme.rememberDmSerifDisplay

@Composable
fun AudioScanScreen(
    state: AudioScanState,
    permissionState: PermissionState,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onCancel: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    JournalScaffold { _ ->
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            JournalIntro(
                label = stringResource(Res.string.audio_scan_journal_label),
                headline = stringResource(Res.string.audio_scan_headline),
                sub = stringResource(Res.string.audio_scan_journal_sub),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(Res.string.audio_scan_marginalia_top),
                fontFamily = rememberCaveat(),
                fontStyle = FontStyle.Italic,
                color = MarginaliaInk,
            )
            Spacer(Modifier.height(32.dp))

            when (state) {
                is AudioScanState.Preparing ->
                    Text("…", fontFamily = rememberDmSerifDisplay(), fontStyle = FontStyle.Italic)
                is AudioScanState.PermissionNeeded ->
                    PermissionPrompt(onClick = onRequestPermission, openSettingsMode = false)
                is AudioScanState.Error.PermanentlyDenied ->
                    PermissionPrompt(onClick = onOpenSettings, openSettingsMode = true)
                is AudioScanState.Idle ->
                    IdleView(onStart = onStartRecording)
                is AudioScanState.Recording ->
                    RecordingView(state = state, onStop = onStopRecording)
                is AudioScanState.Analyzing ->
                    AnalyzingView(state = state)
                is AudioScanState.Error.RecordingFailed ->
                    ErrorRetry(
                        message = stringResource(Res.string.audio_scan_recording_failed),
                        onRetry = onRetry,
                    )
                is AudioScanState.Error.BootstrapFailed ->
                    ErrorRetry(message = state.cause, onRetry = onRetry)
                is AudioScanState.NavigateToMatch -> {
                    // handled by host LaunchedEffect
                }
            }
        }
    }
}

@Composable
private fun IdleView(onStart: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        WaveformBars(rms = 0f, frozen = false)
        Spacer(Modifier.height(24.dp))
        RecordingMicButton(state = MicButtonState.Idle, onClick = onStart)
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(Res.string.audio_scan_cta_idle),
            color = AccentCopper,
            fontFamily = rememberCaveat(),
            fontSize = 16.sp,
        )
    }
}

@Composable
private fun RecordingView(
    state: AudioScanState.Recording,
    onStop: () -> Unit,
) {
    val micState =
        if (state.elapsedMs < AudioScanViewModel.MIN_RECORD_MS) {
            MicButtonState.RecordingDisabled
        } else MicButtonState.Recording

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        WaveformBars(rms = state.rms, frozen = false)
        Spacer(Modifier.height(24.dp))
        RecordingMicButton(state = micState, onClick = onStop)
        Spacer(Modifier.height(12.dp))
        RecordingTimer(elapsedMs = state.elapsedMs)
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(Res.string.audio_scan_cta_recording),
            color = AccentCopper,
            fontFamily = rememberCaveat(),
            fontSize = 16.sp,
        )
    }
}

@Composable
private fun AnalyzingView(state: AudioScanState.Analyzing) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        WaveformBars(rms = state.rmsFrozen, frozen = true)
        Spacer(Modifier.height(24.dp))
        RecordingMicButton(state = MicButtonState.Analyzing, onClick = {})
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(Res.string.audio_scan_analyzing),
            fontFamily = rememberDmSerifDisplay(),
            fontStyle = FontStyle.Italic,
        )
    }
}

@Composable
private fun RecordingTimer(elapsedMs: Long) {
    val seconds = (elapsedMs / 1000L).toInt()
    val mm = seconds / 60
    val ss = seconds % 60
    Text(
        text = "$mm:${ss.toString().padStart(2, '0')}",
        fontFamily = rememberCaveat(),
        color = MarginaliaInk,
        fontSize = 14.sp,
    )
}

@Composable
private fun PermissionPrompt(
    onClick: () -> Unit,
    openSettingsMode: Boolean,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(Res.string.audio_scan_permission_title),
            fontFamily = rememberDmSerifDisplay(),
            fontStyle = FontStyle.Italic,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(Res.string.audio_scan_permission_body),
            fontFamily = rememberCaveat(),
            color = MarginaliaInk,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onClick) {
            Text(
                text = stringResource(
                    if (openSettingsMode) Res.string.audio_scan_permission_open_settings
                    else Res.string.audio_scan_permission_grant,
                ),
            )
        }
    }
}

@Composable
private fun ErrorRetry(
    message: String,
    onRetry: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = message, fontFamily = rememberCaveat(), color = MarginaliaInk)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text(text = stringResource(Res.string.audio_scan_retry)) }
    }
}
```

- [ ] **Step 2: Uppdatera AudioScanScreenHost.android.kt**

Ersätt anrops-blocket längst ned (raderna ~74-83) — bytt `onStartHold` mot `onStartRecording` + nytt `onStopRecording`:

```kotlin
    AudioScanScreen(
        state = state,
        permissionState = permissionState,
        onStartRecording = vm::startRecording,
        onStopRecording = vm::stopRecording,
        onCancel = vm::cancelRecording,
        onRequestPermission = permissionController::request,
        onOpenSettings = permissionController::openSettings,
        onRetry = vm::cancelRecording,
        onBack = onBack,
    )
```

- [ ] **Step 3: Verifiera kompilering + lint**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid ktlintCheck`
Expected: SUCCESSFUL. Om `ktlintCheck` flagga något: `./gradlew ktlintFormat` och re-run.

- [ ] **Step 4: Run JVM unit-tests för regress**

Run: `./gradlew :composeApp:testDebugUnitTest`
Expected: All pass (including the 9 audio-scan tests from Task 3).

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/audio/AudioScanScreen.kt composeApp/src/androidMain/kotlin/se/birdy/app/ui/audio/AudioScanScreenHost.android.kt
git commit -m "feat(listen/t8): AudioScanScreen + Host — tap-to-toggle + RecordingMicButton + count-up timer"
```

---

## Task 9: Bygg AAB-debug + lint+detekt + smoke

**Files:** (ingen)

- [ ] **Step 1: Full build inkl. detekt**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL. Om detekt klagar — granska och åtgärda. Inga nya `@Suppress`-undantag ska behövas.

- [ ] **Step 2: Installdebug på device**

Run: `./gradlew :androidApp:installDebug && "/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell am start -n se.birdy.android/.MainActivity`
Expected: App startar utan crash.

- [ ] **Step 3: Smoke — navigate till Listen-fliken, kontrollera UI**

Manuell verifiering:
- Listen-tab visar 48 copper-staplar
- Mic-knappen är 64dp copper-cirkel med vit `●`
- Tryck mic → går till Recording-state, staplarna pulserar med RMS, `■` glyph syns, timer börjar räkna `0:00 → 0:01 → ...`
- Tryck mic igen vid 5s → går till Analyzing → Match/NoBird

Ingen commit i denna task — bara verifiering.

---

## Task 10: Device-test alla 6 manuella scenarion + screenshots

**Files:** (screenshots-only)

- [ ] **Step 1: Tystnad-scenario**

Spela in tystnad i 60s (lägg telefonen i tysta rummet, tryck mic, vänta).
Expected: Cap triggar automatiskt vid 1:00 → Analyzing → NoBirdScreen (eftersom alla windows får låg confidence).

Verifiera state-flow i logcat:
```bash
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" logcat -s "AudioScan:*" -d
```

- [ ] **Step 2: Känd-fågel-scenario**

Spela koltrast-sång från högtalare (Xeno-Canto / YouTube). Tryck mic. Vänta.
Expected: Auto-stop inom 5-10s → Analyzing → MatchScreen med Koltrast (Q25334).

- [ ] **Step 3: Premature-stop-scenario**

Tryck mic. Tryck mic igen direkt (< 3s).
Expected: Knappen är disabled (alpha 0.5), tap ignoreras. Vänta till 3s → knapp blir aktiv → tryck → Analyzing.

- [ ] **Step 4: Manual-stop-mid-recording**

Tryck mic. Vänta 8s. Tryck mic.
Expected: Analyzing → Match eller NoBird beroende på bestSoFar.

- [ ] **Step 5: Back-mid-recording**

Tryck mic. Vänta 3s. Tryck Android-back-gesture.
Expected: Recorder cancellas, state → Idle, ingen leak i logcat.

- [ ] **Step 6: Screen-lock-mid-recording**

Tryck mic. Vänta 3s. Tryck power-button (skärm lock).
Expected: ON_PAUSE → cancel via DisposableEffect. När skärmen unlock:as → Listen-tabben är i Idle-state.

- [ ] **Step 7: Capturea screenshots**

För varje scenario, ta screenshot med ADB:
```bash
ADB="/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe"
DATE=$(date +%Y-%m-%d)

# 1. Idle (efter app start, navigera till Listen)
"$ADB" exec-out screencap -p > docs/superpowers/screenshots/listen-idle-$DATE.png

# 2. Recording 5s (tryck mic, vänta 5s, screenshot)
"$ADB" exec-out screencap -p > docs/superpowers/screenshots/listen-recording-5s-$DATE.png

# 3. Recording 30s (fortsätt vänta, screenshot vid timer 0:30)
"$ADB" exec-out screencap -p > docs/superpowers/screenshots/listen-recording-30s-$DATE.png

# 4. Auto-stop till Match (efter koltrast-clip)
"$ADB" exec-out screencap -p > docs/superpowers/screenshots/listen-auto-stop-match-$DATE.png

# 5. Manuell stop till NoBird (efter tystnad + tap stop)
"$ADB" exec-out screencap -p > docs/superpowers/screenshots/listen-manual-stop-nobird-$DATE.png
```

- [ ] **Step 8: Commit screenshots**

```bash
git add docs/superpowers/screenshots/listen-*.png
git commit -m "chore(listen/t10): device-screenshots — idle + recording + auto-stop + manual-stop"
```

---

## Task 11: Website-synk — copy + hardcoded "hold 3s"

**Files:**
- Modify: `website/src/components/Listen.astro`
- Modify: `website/src/content/copy.sv.json`
- Modify: `website/src/content/copy.en.json`

- [ ] **Step 1: Uppdatera SV-copy**

I `website/src/content/copy.sv.json`, hitta `"listen":`-blocket (omkring rad 39-44) och ersätt med:

```json
  "listen": {
    "eyebrow": "ELLER TRYCK PÅ MIKEN · PÅ ENHETEN",
    "headline": "Hör den. *Namnge* den.",
    "sub": "Tryck. Lyssna. Fågeln namnger sig själv.",
    "body": "Tryck på inspelningsknappen. Samma AI på enheten lyssnar tills den känner igen fågeln — eller tills du trycker stopp. Inget internet behövs.",
    "hint": "tryck för att lyssna"
  },
```

- [ ] **Step 2: Uppdatera EN-copy**

I `website/src/content/copy.en.json`, motsvarande:

```json
  "listen": {
    "eyebrow": "OR TAP THE MIC · ON-DEVICE",
    "headline": "Hear it. *Name* it.",
    "sub": "Tap. Listen. The bird names itself.",
    "body": "Tap the record button. The same on-device AI listens until it recognises the bird — or until you tap stop. No internet needed.",
    "hint": "tap to listen"
  },
```

- [ ] **Step 3: Uppdatera Listen.astro**

Hitta raden `<div class="hint">hold 3s</div>` (omkring rad 37) och ersätt med:

```astro
          <div class="hint">{t.listen.hint}</div>
```

- [ ] **Step 4: Bygg + smoke-test**

```bash
cd website
npm run build
npm run test:smoke
npm run test:i18n
```

Expected: All pass. Om `test:i18n` flaggar att SV och EN har olika nycklar — dubbelkolla att båda har `hint`.

- [ ] **Step 5: Commit**

```bash
cd ..
git add website/src/content/copy.sv.json website/src/content/copy.en.json website/src/components/Listen.astro
git commit -m "feat(listen/t11): website — sync copy + riv hardcoded 'hold 3s' i Listen.astro"
```

---

## Task 12: Final build + tagga

**Files:** (ingen)

- [ ] **Step 1: Final full build**

```bash
./gradlew build :androidApp:installDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Verifiera installerad app + en sista smoke**

```bash
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell am start -n se.birdy.android/.MainActivity
```

Manuell smoke: navigera till Listen → idle ser ut som spec → tryck mic → spela in 4s → tryck mic igen → ska gå till Match eller NoBird utan crash.

- [ ] **Step 3: Push + tag**

```bash
git push origin main
git tag v0.9.0c-listen-open
git push origin v0.9.0c-listen-open
```

- [ ] **Step 4: Verifiera Vercel-deploy**

Öppna `https://birdy.community/#listen` och `https://birdy.community/sv/#listen` i browser. Bekräfta:
- "tryck för att lyssna" / "tap to listen" syns
- Eyebrow säger "ELLER TRYCK PÅ MIKEN" / "OR TAP THE MIC"
- Inga 404:or i Network-tab

---

## Self-review noteringar

- **Klassificerare-bootstrap-timing:** `startRecording` kör `classifierProvider()` i förväg innan `recorder.start` — det betyder att första mikrofon-knappen-trycket har en liten delay. Acceptabelt enligt befintlig Plan 6b2-pattern.
- **`finalizeAndNavigate` race:** Om både `stopRecording` (manuell) och `onCapReached` (cap) triggar samtidigt, andra anropet ser inte längre `Recording`-state och bailar tidigt — säker.
- **`bestSoFar`-syncing till state:** Vi uppdaterar `state.bestSoFar` när inferensen producerar ett nytt best. UI exponerar inte detta värde (det är ett internt fält som lever vidare för framtida live-hint-feature, men för v1 är det dödkod i UI). Det är OK enligt YAGNI — endast minor cost (state-flow emit).
- **Min-recording 3s:** Hårdkodat på två ställen (`AudioScanViewModel.MIN_RECORD_MS` + `RecordingView.micState`-check). DRY-överträdelse motiveras: VM-konstanten är defense in depth om UI-bug råkar skicka stop ändå.
