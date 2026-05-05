@file:OptIn(kotlinx.coroutines.FlowPreview::class)

package se.birdy.app.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import se.birdy.ml.BirdClassifier
import se.birdy.ml.CameraSource
import se.birdy.ml.Classification
import se.birdy.ml.ImageInput

class ScanViewModel(
    private val classifier: BirdClassifier,
    private val cameraSourceFactory: () -> CameraSource,
    private val samplePeriodMs: Long = 333L,
    private val confidenceThreshold: Float = 0.35f,
) : ViewModel() {
    private val _state = MutableStateFlow<ScanUiState>(ScanUiState.PermissionRequired)
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    private var cameraSource: CameraSource? = null
    private var lastClassification: Classification? = null
    private var consecutiveErrors: Int = 0
    private var currentSamplePeriodMs: Long = samplePeriodMs
    private val latencies = ArrayDeque<Long>()

    fun onPermissionResult(granted: Boolean) {
        if (granted) {
            _state.value = ScanUiState.Idle
            startPipeline()
        } else {
            _state.value = ScanUiState.PermissionDenied
        }
    }

    private fun startPipeline() {
        if (cameraSource != null) return
        val source = cameraSourceFactory()
        cameraSource = source
        val frameChannel = Channel<ImageInput>(capacity = Channel.CONFLATED)

        viewModelScope.launch {
            runCatching { source.start() }
        }

        viewModelScope.launch {
            source.frames().collect { frame -> frameChannel.trySend(frame) }
        }

        viewModelScope.launch {
            val throttled = frameChannel.consumeAsFlow()
            val sampled = if (currentSamplePeriodMs > 0L) throttled.sample(currentSamplePeriodMs) else throttled
            sampled.collect { frame -> processFrame(frame) }
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
            _state.value = ScanUiState.Error("Klassificeraren misslyckades upprepade gånger")
            return
        }
        if (result == null) return
        lastClassification = result

        val latency = nowMillis() - started
        latencies.addLast(latency)
        if (latencies.size > 10) latencies.removeFirst()
        val sorted = latencies.sorted()
        val p95 = sorted[(sorted.size * 95 / 100).coerceAtMost(sorted.size - 1)]
        currentSamplePeriodMs = if (p95 > 333L) 666L else 333L

        val top = result.top()
        val top1 = top?.takeIf { it.confidence >= confidenceThreshold }
        _state.value =
            ScanUiState.Scanning(
                top1 = top1,
                isThrottled = currentSamplePeriodMs == 666L,
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
        cameraSource?.let { src ->
            viewModelScope.launch { runCatching { src.stop() } }
        }
        classifier.close()
    }

    private fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()
}
