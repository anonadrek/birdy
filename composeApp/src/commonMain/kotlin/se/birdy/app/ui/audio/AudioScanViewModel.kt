package se.birdy.app.ui.audio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
            PermissionState.Unknown -> _state.value = AudioScanState.Preparing
        }
    }

    fun startRecording() {
        if (_state.value !is AudioScanState.Idle) return
        _state.value = AudioScanState.Recording(rms = 0f, elapsedMs = 0L)
        val t0 = clock()
        recordingJob =
            viewModelScope.launch {
                try {
                    val pcm =
                        recorder.record3s(
                            onLevel = { rms ->
                                val elapsed = clock() - t0
                                val s = _state.value
                                if (s is AudioScanState.Recording) {
                                    _state.value = AudioScanState.Recording(rms, elapsed)
                                }
                            },
                        )
                    val frozen = (_state.value as? AudioScanState.Recording)?.rms ?: 0f
                    _state.value = AudioScanState.Analyzing(rmsFrozen = frozen)
                    analyzeAndNavigate(pcm)
                } catch (t: Throwable) {
                    _state.value = AudioScanState.Error.RecordingFailed
                }
            }
    }

    fun cancelRecording() {
        recordingJob?.cancel()
        recordingJob = null
        if (_state.value !is AudioScanState.Error) _state.value = AudioScanState.Idle
    }

    private suspend fun analyzeAndNavigate(pcm: ShortArray) {
        val pair =
            runCatching { classifierProvider() }.getOrElse {
                _state.value = AudioScanState.Error.BootstrapFailed(it.message ?: "bootstrap failed")
                return
            }
        val classifier = pair.first
        classifierMode = pair.second

        val ts = clock()
        val pngPath =
            withContext(ioDispatcher) {
                waveformRenderer.renderWaveformPng(pcm, "${audioStorageDir()}/$ts.png")
            }
        val audioPath =
            withContext(ioDispatcher) {
                waveformRenderer.encodeOpus(pcm, "${audioStorageDir()}/$ts.opus")
            }
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

        val json = Json.encodeToString(ScanSourceSerialization.serializer(), source.toSerial())
        _state.value = AudioScanState.NavigateToMatch(json)
    }
}

interface AudioRecorderApi {
    suspend fun record3s(onLevel: (Float) -> Unit): ShortArray
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
