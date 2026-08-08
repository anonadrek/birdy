package se.birdy.app.ui.audio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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
 *   Idle → Recording (sliding 3s/1s windows classified during capture;
 *          every result accumulates into a per-species session-max map)
 *        → Analyzing (ranks the session accumulator into a top-3 list — no
 *          re-classification; falls back to a single classify of the last
 *          window only when the accumulator is empty)
 *        → NavigateToMatch / Error.*
 *
 * Auto-stops when top-1 confidence reaches [AUTO_STOP_THRESHOLD] over any
 * sliding window. Manual stop via [stopRecording] or 60s cap have identical
 * finalize behavior.
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
    private val speciesNameLookup: suspend (String) -> String? = { null },
) : ViewModel() {
    private val _state = MutableStateFlow<AudioScanState>(AudioScanState.Preparing)
    val state: StateFlow<AudioScanState> = _state

    private val _demoMode = MutableStateFlow(false)

    /** True när fejk-klassificeraren är aktiv (endast debug-byggen) — UI visar banner. */
    val demoMode: StateFlow<Boolean> = _demoMode

    private var sessionJob: Job? = null
    private var inferenceJob: Job? = null

    @Volatile private var finalizeJob: Job? = null
    private var handle: RecorderHandle? = null

    // Streaming-state, owned by sessionJob coroutine
    @Volatile private var fullBuffer = ShortArray(0)

    @Volatile private var bufferEnd = 0

    /** Per-art sessionsmax över alla fönster: speciesId -> bästa observation. */
    @Volatile private var sessionScores: Map<String, Top1> = emptyMap()

    @Volatile private var lastClassifiedAtSamples = 0

    @Volatile private var inflight = false

    @Volatile private var sessionStartMs: Long = 0L

    @Volatile private var classifierInstance: BirdAudioClassifier? = null

    /** Per-session cache av speciesId -> lokaliserat namn (eller null om lookup misslyckades). */
    private val nameCache = mutableMapOf<String, String?>()

    companion object {
        const val SAMPLE_RATE = 48_000
        const val WINDOW_SAMPLES = SAMPLE_RATE * 3 // 144_000
        const val STRIDE_SAMPLES = SAMPLE_RATE // 48_000 → 1s
        const val AUTO_STOP_THRESHOLD = 0.65f
        const val MIN_RECORD_MS = 3_000L
        const val MAX_RECORD_MS = 60_000L
        const val MAX_BUFFER_SAMPLES = SAMPLE_RATE * 60 // 60s
        const val ANALYZE_TIMEOUT_MS = 15_000L
    }

    private fun logAudio(msg: String) = println("Birdy/audio: $msg")

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
        sessionScores = emptyMap()
        lastClassifiedAtSamples = 0
        inflight = false
        sessionStartMs = clock()
        nameCache.clear()

        sessionJob =
            viewModelScope.launch {
                try {
                    val (clf, mode) =
                        runCatching { classifierProvider() }
                            .getOrElse { throwable ->
                                coroutineContext.ensureActive()
                                logAudio("classifier bootstrap failed: ${throwable.message}")
                                _state.value =
                                    AudioScanState.Error.BootstrapFailed(throwable.message ?: "bootstrap failed")
                                return@launch
                            }
                    classifierInstance = clf
                    _demoMode.value = mode == AudioClassifierMode.DEMO

                    handle =
                        recorder.start(
                            onChunk = { samples, rms, totalSoFar ->
                                onChunkReceived(samples, rms, totalSoFar)
                            },
                            onCapReached = {
                                finalizeJob = viewModelScope.launch { finalizeAndNavigate(reason = StopReason.CAP) }
                            },
                            onError = { t -> onRecorderError(t) },
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

    /** Recorder-haveri mitt i sessionen: städa och visa fel istället för tyst frusen timer. */
    private fun onRecorderError(cause: Throwable) {
        logAudio("recorder failed mid-session: ${cause.message}")
        viewModelScope.launch {
            inferenceJob?.cancel()
            handle?.cancel()
            handle = null
            if (_state.value is AudioScanState.Recording) {
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
            // bestSoFar is owned by maybeSubmitInference (derived from sessionScores);
            // preserve whatever it last set instead of re-deriving on every chunk.
            if (s is AudioScanState.Recording) s.copy(rms = rms, elapsedMs = elapsed) else s
        }

        maybeSubmitInference()
    }

    private suspend fun resolveName(speciesId: String): String? =
        if (nameCache.containsKey(speciesId)) {
            nameCache[speciesId]
        } else {
            val name =
                try {
                    speciesNameLookup(speciesId)
                } catch (t: CancellationException) {
                    throw t
                } catch (t: Throwable) {
                    null
                }
            nameCache[speciesId] = name
            name
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
                result.results.forEach { r ->
                    val existing = sessionScores[r.speciesId]
                    if (existing == null || r.confidence > existing.confidence) {
                        sessionScores = sessionScores +
                            (r.speciesId to Top1(r.speciesId, r.confidence))
                    }
                }
                if (result.results.isNotEmpty()) {
                    val best = sessionScores.values.maxByOrNull { it.confidence }
                    val bestName = best?.let { resolveName(it.speciesId) }
                    _state.update { s ->
                        if (s is AudioScanState.Recording) {
                            s.copy(bestSoFar = best, bestSoFarName = bestName)
                        } else {
                            s
                        }
                    }
                }
                if (top != null && top.confidence >= AUTO_STOP_THRESHOLD) {
                    // Finalize FÅR INTE köras inline här: denna coroutine är barn till
                    // inferenceJob, och finalizeAndNavigate cancellar inferenceJob →
                    // self-cancel → permanent Analyzing-häng (i produktion t.o.m. vC126).
                    finalizeJob = viewModelScope.launch { finalizeAndNavigate(reason = StopReason.AUTO) }
                    return@launch
                }
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                // Per-fönster klassificeringsfel (t.ex. AudioSessionFailureGuard-rethrow innan
                // degradering) ska logga och släppa fönstret, inte krascha en ofångad coroutine.
                logAudio("streaming classify failed: ${t.message}")
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
        finalizeJob = viewModelScope.launch { finalizeAndNavigate(reason = StopReason.MANUAL) }
    }

    private suspend fun finalizeAndNavigate(reason: StopReason) {
        val current = _state.value as? AudioScanState.Recording ?: return
        val analyzing = AudioScanState.Analyzing(rmsFrozen = current.rms)
        if (!_state.compareAndSet(current, analyzing)) return

        // Cancel any in-flight streaming inference — sessionScores is read below
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

        val windowEnd = fullPcm.size
        val windowStart = (windowEnd - WINDOW_SAMPLES).coerceAtLeast(0)
        val window = fullPcm.copyOfRange(windowStart, windowEnd)

        try {
            withTimeout(ANALYZE_TIMEOUT_MS) {
                analyzeAndNavigate(fullPcm = fullPcm, window = window)
            }
        } catch (t: TimeoutCancellationException) {
            logAudio("analyze timed out after ${ANALYZE_TIMEOUT_MS}ms")
            _state.update { s -> if (s is AudioScanState.Analyzing) AudioScanState.Error.AnalyzeFailed else s }
        } catch (t: CancellationException) {
            throw t
        } catch (t: Throwable) {
            logAudio("analyze failed: ${t.message}")
            _state.update { s -> if (s is AudioScanState.Analyzing) AudioScanState.Error.AnalyzeFailed else s }
        }
    }

    private suspend fun analyzeAndNavigate(
        fullPcm: ShortArray,
        window: ShortArray,
    ) {
        coroutineContext.ensureActive()
        val ranked = sessionScores.values.sortedByDescending { it.confidence }.take(3)
        val results: List<ClassificationResult> =
            if (ranked.isNotEmpty()) {
                // Sessions-ackumulatorn ersätter om-klassificeringen av bästa fönstret —
                // en inferens mindre, och Disambig får riktiga kandidater.
                ranked.map { ClassificationResult(it.speciesId, it.confidence) }
            } else {
                val classifier =
                    classifierInstance ?: run {
                        _state.value = AudioScanState.Error.BootstrapFailed("classifier unavailable")
                        return
                    }
                val waveform = normalizer(window)
                classifier.classify(AudioInput(waveform, SAMPLE_RATE, 3_000, rawPcm = window)).results
            }

        val ts = clock()
        val pngPath: String =
            try {
                withContext(ioDispatcher) {
                    waveformRenderer.renderWaveformPng(fullPcm, "${audioStorageDir()}/$ts.png")
                }
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                logAudio("waveform png failed: ${t.message}")
                "" // ""-konventionen: MatchResult ifBlank{null}-ar redan frameJpegPath
            }
        coroutineContext.ensureActive()
        val audioPath: String? =
            try {
                withContext(ioDispatcher) {
                    waveformRenderer.encodeOpus(fullPcm, "${audioStorageDir()}/$ts.opus")
                }
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                logAudio("opus encode failed: ${t.message}")
                null
            }
        coroutineContext.ensureActive()

        val source =
            ScanSource.Audio(
                frameJpegPath = pngPath,
                classification = Classification(results = results, frameTimestampMillis = ts),
                audioWavPath = audioPath,
            )
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
        finalizeJob?.cancel()
        finalizeJob = null
        handle?.cancel()
        handle = null
        sessionScores = emptyMap()
        bufferEnd = 0
        inflight = false
        lastClassifiedAtSamples = 0
        if (_state.value is AudioScanState.Recording ||
            _state.value is AudioScanState.Error ||
            _state.value is AudioScanState.Analyzing
        ) {
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
     * - [onError] fyras högst en gång per session när capture havererar mitt i
     *   (read <= 0 = mic stulen/privacy-toggle/backgrounding, eller throw i
     *   capture-loopen). Fyras INTE vid normal stop/cancel. Fyras på recorderns
     *   IO-tråd.
     */
    fun start(
        onChunk: (samples: ShortArray, rms: Float, totalSamplesSoFar: Int) -> Unit,
        onCapReached: () -> Unit,
        onError: (Throwable) -> Unit = {},
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

    /**
     * Returnerar null när encoding inte stöds på plattformen (Android API < 29
     * saknar Opus-encoder + OGG-muxer). Fel kastas; anroparen degraderar.
     */
    suspend fun encodeOpus(
        pcm: ShortArray,
        outPath: String,
    ): String?
}
