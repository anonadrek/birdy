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
import kotlinx.datetime.Clock
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
import kotlin.concurrent.Volatile
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
    private val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val normalizer: (ShortArray) -> FloatArray = ::normalize,
    private val ioDispatcher: CoroutineDispatcher = se.birdy.app.util.ioDispatcher,
    private val inferenceDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val _state = MutableStateFlow<AudioScanState>(AudioScanState.Preparing)
    val state: StateFlow<AudioScanState> = _state

    private var sessionJob: Job? = null
    private var inferenceJob: Job? = null
    private var handle: RecorderHandle? = null

    // Streaming-state, owned by sessionJob coroutine
    @Volatile private var fullBuffer = ShortArray(0)

    @Volatile private var bufferEnd = 0

    @Volatile private var bestSoFar: Top1? = null

    @Volatile private var lastClassifiedAtSamples = 0

    @Volatile private var inflight = false

    @Volatile private var sessionStartMs: Long = 0L

    @Volatile private var classifierInstance: BirdAudioClassifier? = null

    companion object {
        const val SAMPLE_RATE = 48_000
        const val WINDOW_SAMPLES = SAMPLE_RATE * 3 // 144_000
        const val STRIDE_SAMPLES = SAMPLE_RATE // 48_000 → 1s
        const val AUTO_STOP_THRESHOLD = 0.65f
        const val MIN_RECORD_MS = 3_000L
        const val MAX_RECORD_MS = 60_000L
        const val MAX_BUFFER_SAMPLES = SAMPLE_RATE * 60 // 60s
    }

    fun onPermissionState(p: PermissionState) {
        when (p) {
            PermissionState.Granted ->
                if (_state.value !is AudioScanState.Recording) {
                    _state.value = AudioScanState.Idle
                }
            PermissionState.Denied -> _state.value = AudioScanState.PermissionNeeded
            PermissionState.PermanentlyDenied -> _state.value = AudioScanState.Error.PermanentlyDenied
            PermissionState.Unknown -> _state.value = AudioScanState.PermissionNeeded
        }
    }

    fun startRecording() {
        val initial = AudioScanState.Recording(rms = 0f, elapsedMs = 0L)
        if (!_state.compareAndSet(AudioScanState.Idle, initial)) return
        sessionJob?.cancel()
        inferenceJob?.cancel()
        inferenceJob = Job(viewModelScope.coroutineContext[Job])

        fullBuffer = ShortArray(MAX_BUFFER_SAMPLES)
        bufferEnd = 0
        bestSoFar = null
        lastClassifiedAtSamples = 0
        inflight = false
        sessionStartMs = clock()

        sessionJob =
            viewModelScope.launch {
                try {
                    classifierInstance =
                        runCatching { classifierProvider() }
                            .getOrElse { throwable ->
                                coroutineContext.ensureActive()
                                _state.value = AudioScanState.Error.BootstrapFailed(throwable.message ?: "bootstrap failed")
                                return@launch
                            }.first

                    handle =
                        recorder.start(
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

    private fun onChunkReceived(
        samples: ShortArray,
        rms: Float,
        totalSoFar: Int,
    ) {
        val toCopy = minOf(samples.size, fullBuffer.size - bufferEnd)
        if (toCopy > 0) {
            samples.copyInto(fullBuffer, destinationOffset = bufferEnd, startIndex = 0, endIndex = toCopy)
            bufferEnd += toCopy
        }

        val elapsed = clock() - sessionStartMs
        _state.update { s ->
            if (s is AudioScanState.Recording) {
                AudioScanState.Recording(rms = rms, elapsedMs = elapsed, bestSoFar = bestSoFar)
            } else {
                s
            }
        }

        maybeSubmitInference()
    }

    private fun maybeSubmitInference() {
        if (inflight) return
        if (bufferEnd < WINDOW_SAMPLES) return
        if (bufferEnd - lastClassifiedAtSamples < STRIDE_SAMPLES && lastClassifiedAtSamples > 0) return

        // Check parent job BEFORE mutating state so an inactive parent doesn't
        // leave inflight=true stuck (only cleared by cancelRecording otherwise).
        val parent = inferenceJob?.takeIf { it.isActive } ?: return

        val windowEnd = bufferEnd
        val windowStart = windowEnd - WINDOW_SAMPLES
        val window = fullBuffer.copyOfRange(windowStart, windowEnd)
        lastClassifiedAtSamples = bufferEnd
        inflight = true

        viewModelScope.launch(parent + inferenceDispatcher) {
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
                        // Finalize FÅR INTE köras inline här: denna coroutine är barn till
                        // inferenceJob, och finalizeAndNavigate cancellar inferenceJob →
                        // self-cancel → permanent Analyzing-häng (i produktion t.o.m. vC126).
                        viewModelScope.launch { finalizeAndNavigate(reason = StopReason.AUTO) }
                        return@launch
                    }
                }
            } finally {
                inflight = false
            }
        }
    }

    fun stopRecording() {
        if (_state.value !is AudioScanState.Recording) return
        // Guard: need at least one full 3s window before allowing manual stop.
        // Using sample count rather than wall-clock so tests with frozen clocks work correctly.
        if (bufferEnd < WINDOW_SAMPLES) return
        viewModelScope.launch { finalizeAndNavigate(reason = StopReason.MANUAL) }
    }

    private suspend fun finalizeAndNavigate(reason: StopReason) {
        val current = _state.value as? AudioScanState.Recording ?: return
        val analyzing = AudioScanState.Analyzing(rmsFrozen = current.rms)
        if (!_state.compareAndSet(current, analyzing)) return

        // Cancel any in-flight streaming inference — bestSoFar is captured below
        // and further writes would be wasted work past the Analyzing transition.
        inferenceJob?.cancel()

        val fullPcm =
            try {
                handle?.stopAndFlush() ?: ShortArray(bufferEnd)
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                fullBuffer.copyOf(bufferEnd)
            }

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

    private suspend fun analyzeAndNavigate(
        fullPcm: ShortArray,
        window: ShortArray,
    ) {
        coroutineContext.ensureActive()
        val classifier =
            classifierInstance ?: run {
                _state.value = AudioScanState.Error.BootstrapFailed("classifier unavailable")
                return
            }

        val ts = clock()
        val pngPath =
            withContext(ioDispatcher) {
                waveformRenderer.renderWaveformPng(fullPcm, "${audioStorageDir()}/$ts.png")
            }
        coroutineContext.ensureActive()
        val audioPath =
            withContext(ioDispatcher) {
                waveformRenderer.encodeOpus(fullPcm, "${audioStorageDir()}/$ts.opus")
            }
        coroutineContext.ensureActive()
        val waveform = normalizer(window)
        val classification = classifier.classify(AudioInput(waveform, SAMPLE_RATE, 3_000, rawPcm = window))

        val source =
            if (classification.results.isEmpty()) {
                ScanSource.Audio(
                    frameJpegPath = pngPath,
                    classification = Classification(results = emptyList(), frameTimestampMillis = ts),
                    audioWavPath = audioPath,
                )
            } else {
                val top = classification.results.first()
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

        val json = Json.encodeToString(ScanSourceSerialization.serializer(), source.toSerial())
        _state.update { s ->
            if (s is AudioScanState.Analyzing) AudioScanState.NavigateToMatch(json, ts) else s
        }
    }

    fun cancelRecording() {
        sessionJob?.cancel()
        sessionJob = null
        inferenceJob?.cancel()
        inferenceJob = null
        handle?.cancel()
        handle = null
        bestSoFar = null
        bufferEnd = 0
        inflight = false
        lastClassifiedAtSamples = 0
        if (_state.value is AudioScanState.Recording || _state.value is AudioScanState.Error) {
            _state.value = AudioScanState.Idle
        }
    }

    private enum class StopReason { AUTO, MANUAL, CAP }
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
     * Idempotent: subsequent calls return content-equal arrays of the captured PCM
     * (implementations MAY allocate fresh copies — callers must not rely on identity).
     * If [cancel] was called first, returns an empty ShortArray.
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
