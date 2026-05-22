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
 * Orchestrates the audio-scan flow:
 * Preparing → PermissionNeeded / Error.PermanentlyDenied / Idle
 * Idle → Recording → Analyzing → NavigateToMatch / Error.*
 *
 * [classifierProvider] returns a [Pair] of [BirdAudioClassifier] and [AudioClassifierMode]
 * so that a future DEMO banner (T7) can read the mode. The UI only uses the classifier
 * in T5; mode surfacing is deferred to T7.
 */
class AudioScanViewModel(
    private val classifierProvider: suspend () -> Pair<BirdAudioClassifier, AudioClassifierMode>,
    private val recorder: AudioRecorderApi,
    private val waveformRenderer: WaveformRendererApi,
    private val audioStorageDir: () -> String,
    private val clock: () -> Long = { System.currentTimeMillis() },
    /**
     * Converts raw 16-bit PCM to float32 normalised waveform for ML input.
     * Defaults to [normalize] which is Android-only; inject a stub in unit tests.
     */
    private val normalizer: (ShortArray) -> FloatArray = ::normalize,
    /**
     * Dispatcher used for IO-bound operations (waveform write, Opus encode).
     * Defaults to [Dispatchers.IO]; inject [Dispatchers.Unconfined] in unit tests.
     */
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val _state = MutableStateFlow<AudioScanState>(AudioScanState.Preparing)
    val state: StateFlow<AudioScanState> = _state

    // Stored for future T7 DEMO-banner work; not surfaced in UI yet.
    @Suppress("UnusedPrivateMember")
    private var classifierMode: AudioClassifierMode? = null

    private var recordingJob: Job? = null

    fun onPermissionState(p: PermissionState) {
        when (p) {
            PermissionState.Granted ->
                if (_state.value !is AudioScanState.Recording) {
                    _state.value = AudioScanState.Idle
                }
            PermissionState.Denied -> _state.value = AudioScanState.PermissionNeeded(canRequest = true)
            PermissionState.PermanentlyDenied -> _state.value = AudioScanState.Error.PermanentlyDenied
            // First launch (never asked) is treated like Denied so the user sees the
            // journal-style "Birdy needs your microphone" prompt with a Grant button
            // instead of being stuck on the "…" Preparing placeholder.
            PermissionState.Unknown -> _state.value = AudioScanState.PermissionNeeded(canRequest = true)
        }
    }

    fun startRecording() {
        val initial = AudioScanState.Recording(rms = 0f, elapsedMs = 0L)
        if (!_state.compareAndSet(AudioScanState.Idle, initial)) return
        recordingJob?.cancel()
        @Suppress("UNUSED_VARIABLE")
        val t0 = clock()
        recordingJob =
            viewModelScope.launch {
                try {
                    // TODO(listen/t3): replace with streaming recorder.start() + sliding-window
                    //   classification. This body is rewritten entirely in Task 3.
                    @Suppress("UNREACHABLE_CODE")
                    val pcm: ShortArray = TODO("streaming startRecording implemented in Task 3")
                    val frozen = (_state.value as? AudioScanState.Recording)?.rms ?: 0f
                    _state.value = AudioScanState.Analyzing(rmsFrozen = frozen)
                    analyzeAndNavigate(pcm)
                } catch (t: CancellationException) {
                    throw t
                } catch (t: Throwable) {
                    _state.value = AudioScanState.Error.RecordingFailed
                }
            }
    }

    fun cancelRecording() {
        recordingJob?.cancel()
        recordingJob = null
        val current = _state.value
        if (current is AudioScanState.Recording || current is AudioScanState.Error) {
            _state.value = AudioScanState.Idle
        }
    }

    private suspend fun analyzeAndNavigate(pcm: ShortArray) {
        coroutineContext.ensureActive()
        val classifier =
            runCatching { classifierProvider() }
                .getOrElse { throwable ->
                    coroutineContext.ensureActive()
                    val message = throwable.message ?: "bootstrap failed"
                    _state.update { s ->
                        if (s is AudioScanState.Analyzing) AudioScanState.Error.BootstrapFailed(message) else s
                    }
                    return
                }.also { pair -> classifierMode = pair.second }
                .first

        coroutineContext.ensureActive()
        val ts = clock()
        val pngPath =
            withContext(ioDispatcher) {
                waveformRenderer.renderWaveformPng(pcm, "${audioStorageDir()}/$ts.png")
            }
        coroutineContext.ensureActive()
        val audioPath =
            withContext(ioDispatcher) {
                waveformRenderer.encodeOpus(pcm, "${audioStorageDir()}/$ts.opus")
            }
        coroutineContext.ensureActive()
        val waveform = normalizer(pcm)
        val audioClassification = classifier.classify(AudioInput(waveform, 48_000, 3_000, rawPcm = pcm))

        val source =
            if (audioClassification.results.isEmpty()) {
                ScanSource.Audio(
                    frameJpegPath = pngPath,
                    classification = Classification(results = emptyList(), frameTimestampMillis = ts),
                    audioWavPath = audioPath,
                )
            } else {
                val top = audioClassification.results.first()
                ScanSource.Audio(
                    frameJpegPath = pngPath,
                    classification =
                        Classification(
                            results = listOf(ClassificationResult(top.speciesId, top.confidence)),
                            frameTimestampMillis = ts,
                        ),
                    audioWavPath = audioPath,
                )
            }

        coroutineContext.ensureActive()
        val json = Json.encodeToString(ScanSourceSerialization.serializer(), source.toSerial())
        _state.update { current ->
            if (current is AudioScanState.Analyzing) AudioScanState.NavigateToMatch(json, ts) else current
        }
    }
}

interface AudioRecorderApi {
    /**
     * Open-ended capture. Returns a handle as soon as AudioRecord is initialised.
     * Emits PCM chunks via [onChunk] until [RecorderHandle.stopAndFlush] is called
     * or [maxDurationMs] elapses (in which case [onCapReached] fires).
     *
     * - [onChunk] runs on the recorder's IO dispatcher; consumer MUST be cheap
     *   (append to buffer, update rms) — heavy work (ML inference) belongs in
     *   the ViewModel on its own dispatcher.
     * - Callbacks fire on the recorder's IO thread, NOT the caller's thread,
     *   and may arrive after [start] has returned the handle. The consumer
     *   must store incoming chunks in a thread-safe way.
     */
    fun start(
        onChunk: (samples: ShortArray, rms: Float, totalSamplesSoFar: Int) -> Unit,
        onCapReached: () -> Unit,
        maxDurationMs: Long = 60_000L,
    ): RecorderHandle
}

interface RecorderHandle {
    /**
     * Stop recorder, flush remaining buffered chunks, and return the full captured PCM.
     * Idempotent: subsequent calls return the same ShortArray instance. If [cancel]
     * was called first, returns an empty ShortArray.
     */
    suspend fun stopAndFlush(): ShortArray

    /**
     * Cancel and discard all captured PCM. Idempotent.
     * No-op if [stopAndFlush] has already returned — the flushed array stays valid
     * (we cannot retroactively un-flush) but no further chunks will be delivered.
     */
    fun cancel()
}

interface WaveformRendererApi {
    suspend fun renderWaveformPng(
        pcm: ShortArray,
        outPath: String,
    ): String

    suspend fun encodeOpus(
        pcm: ShortArray,
        outPath: String,
    ): String
}
