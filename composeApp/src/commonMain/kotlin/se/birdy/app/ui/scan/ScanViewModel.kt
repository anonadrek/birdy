@file:OptIn(
    kotlinx.coroutines.FlowPreview::class,
    kotlinx.coroutines.ExperimentalCoroutinesApi::class,
)

package se.birdy.app.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import se.birdy.ml.BirdClassifier
import se.birdy.ml.CameraSource
import se.birdy.ml.Classification
import se.birdy.ml.ClassifierMode
import se.birdy.ml.ImageInput

class ScanViewModel(
    private val classifier: BirdClassifier,
    cameraSourceFactory: () -> CameraSource,
    val classifierMode: ClassifierMode = ClassifierMode.REAL,
    initialSamplePeriodMs: Long = 333L,
    private val confidenceThreshold: Float = 0.35f,
    private val frameThrottling: Boolean = true,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : ViewModel() {
    private val _state = MutableStateFlow<ScanUiState>(ScanUiState.PermissionRequired)
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    // Single source instance owned by the VM and exposed to the UI so PreviewView
    // and the camera bind to the same CameraSource.
    val cameraSource: CameraSource = cameraSourceFactory()
    private var pipelineStarted: Boolean = false
    private var lastClassification: Classification? = null
    private var consecutiveErrors: Int = 0

    // MutableStateFlow drives dynamic re-sampling: emitting a new period rebuilds
    // the sampled flow via flatMapLatest so the rate change takes effect immediately.
    private val periodFlowMs = MutableStateFlow(initialSamplePeriodMs)
    private val latencies = ArrayDeque<Long>()

    // Drop-oldest shared flow replaces Channel(CONFLATED) so the flow can be
    // collected multiple times across flatMapLatest restarts.
    private val frameSink =
        MutableSharedFlow<ImageInput>(
            replay = 0,
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    fun onPermissionResult(granted: Boolean) {
        if (granted) {
            _state.value = ScanUiState.Idle
            startPipeline()
        } else {
            _state.value = ScanUiState.PermissionDenied
        }
    }

    private fun startPipeline() {
        if (pipelineStarted) return
        pipelineStarted = true

        viewModelScope.launch {
            runCatching { cameraSource.start() }
        }

        // Producer: forward camera frames into the shared drop-oldest sink.
        viewModelScope.launch {
            cameraSource.frames().collect { frame -> frameSink.tryEmit(frame) }
        }

        // Consumer: rebuild the sampled flow whenever the period changes.
        viewModelScope.launch {
            val sampledFrames =
                periodFlowMs.flatMapLatest { period ->
                    if (frameThrottling && period > 0L) frameSink.sample(period) else frameSink
                }
            sampledFrames.collect { frame -> processFrame(frame) }
        }
    }

    private suspend fun processFrame(frame: ImageInput) {
        val started = nowMillis()
        val result =
            runCatching { classifier.classify(frame) }
                .onSuccess { consecutiveErrors = 0 }
                .onFailure { consecutiveErrors += 1 }
                .getOrNull()
        if (consecutiveErrors > 5) {
            _state.value = ScanUiState.Error(ScanErrorKind.ClassifierFailed)
            return
        }
        if (result == null) return
        lastClassification = result

        val latency = nowMillis() - started
        latencies.addLast(latency)
        if (latencies.size > 10) latencies.removeFirst()
        val sorted = latencies.sorted()
        val p95 = sorted[(sorted.size * 95 / 100).coerceAtMost(sorted.size - 1)]
        val newPeriod = if (p95 > 333L) 666L else 333L
        // Only emit when the period actually changes to avoid spurious flatMapLatest restarts.
        if (newPeriod != periodFlowMs.value) periodFlowMs.value = newPeriod

        val top = result.top()
        val top1 = top?.takeIf { it.confidence >= confidenceThreshold }
        _state.value =
            ScanUiState.Scanning(
                top1 = top1,
                isThrottled = periodFlowMs.value == 666L,
            )
    }

    fun onFreeze(
        jpegBytes: ByteArray,
        persist: (ByteArray) -> String,
    ) {
        val classification = lastClassification ?: return
        val path = runCatching { persist(jpegBytes) }.getOrNull() ?: return
        _state.value =
            ScanUiState.FrozenAt(
                predictions = classification.sortedByConfidenceDescending(),
                frameJpegPath = path,
                timestampMillis = classification.frameTimestampMillis,
            )
    }

    fun onResumeAfterFreeze() {
        if (_state.value is ScanUiState.FrozenAt) _state.value = ScanUiState.Idle
    }

    override fun onCleared() {
        // classifier.close() is non-suspend so we run it first; if the process is killed
        // before the GlobalScope job lands, at least the TFLite interpreter releases.
        // cameraSource.stop() is suspend (CameraX bind/unbind goes via the camera-executor)
        // so we have to dispatch it; viewModelScope is already cancelled at this point.
        runCatching { classifier.close() }
        @Suppress("OPT_IN_USAGE")
        GlobalScope.launch(kotlinx.coroutines.Dispatchers.Default + NonCancellable) {
            runCatching { cameraSource.stop() }
        }
    }
}
