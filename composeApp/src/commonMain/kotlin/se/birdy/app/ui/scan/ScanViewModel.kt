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
    // Maps speciesId (raw Wikidata Q-id) -> locale-resolved display name. Loaded once at
    // init from the species catalog so the live chip never shows a raw Q-id. Default empty
    // keeps tests and previews independent of the content layer.
    private val loadNames: suspend () -> Map<String, String> = { emptyMap() },
) : ViewModel() {
    private val _state = MutableStateFlow<ScanUiState>(ScanUiState.PermissionRequired)
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    // Cached id->name map; populated asynchronously at init. Reads are cheap and happen
    // on every frame, so we resolve in-memory rather than hitting the DB per detection.
    private var nameByQid: Map<String, String> = emptyMap()

    init {
        viewModelScope.launch {
            nameByQid = runCatching { loadNames() }.getOrElse { emptyMap() }
        }
    }

    // Single source instance owned by the VM and exposed to the UI so PreviewView
    // and the camera bind to the same CameraSource.
    val cameraSource: CameraSource = cameraSourceFactory()
    private var pipelineStarted: Boolean = false

    // The last COMPLETED classification paired with the exact frame it describes. The live
    // chip renders this result, so freezing on the pair guarantees the user gets what they
    // saw: chip text, routed predictions and persisted photo all describe the same frame.
    // Two designs failed on device before this one (both 2026-06-10): routing on a rolling
    // classification while persisting a separately captured JPEG let a transient frame's
    // result attach to a different photo ("random species" for non-bird scenes), and
    // re-classifying the newest raw frame at freeze time routed on a frame one sample
    // period NEWER than the chip ("chip said Blue Tit 91%, match said Kestrel 68%").
    private var lastClassified: ClassifiedFrame? = null
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
        lastClassified = ClassifiedFrame(frame, result)

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
                displayName = top1?.speciesId?.let { nameByQid[it] },
                isThrottled = periodFlowMs.value == 666L,
            )
    }

    fun onFreeze(persist: (ImageInput) -> String) {
        if (_state.value is ScanUiState.FrozenAt) return
        val snap = lastClassified ?: return
        val path = runCatching { persist(snap.frame) }.getOrNull() ?: return
        // A freeze must match what the user sees NOW. If the camera stalled (observed on
        // device 2026-06-10: indicator off while ScanScreen was still visible) the last
        // pair can be minutes old — surface it as no-detection, which MatchResultViewModel
        // routes to NoBird. The stale photo is still persisted: it is the only frame we
        // have, and NoBird's framing ("a blur, a flicker of wings") absorbs it.
        val isStale = nowMillis() - snap.classification.frameTimestampMillis > FREEZE_FRESHNESS_MS
        _state.value =
            if (isStale) {
                ScanUiState.FrozenAt(
                    predictions = emptyList(),
                    frameJpegPath = path,
                    timestampMillis = nowMillis(),
                )
            } else {
                ScanUiState.FrozenAt(
                    predictions = snap.classification.sortedByConfidenceDescending(),
                    frameJpegPath = path,
                    timestampMillis = snap.classification.frameTimestampMillis,
                )
            }
    }

    fun onResumeAfterFreeze() {
        if (_state.value is ScanUiState.FrozenAt) {
            // The frozen pair belongs to the finished freeze cycle; the pipeline
            // repopulates within one sample period (333/666ms) once frames flow again.
            lastClassified = null
            _state.value = ScanUiState.Idle
        }
    }

    companion object {
        /**
         * Max age for [lastClassified] at freeze time. A healthy pipeline classifies
         * every 333/666ms, so 2s of silence means the camera stalled and the stored
         * pair no longer describes the visible frame.
         */
        const val FREEZE_FRESHNESS_MS = 2_000L
    }

    /** A camera frame and the classification produced from exactly that frame. */
    private data class ClassifiedFrame(
        val frame: ImageInput,
        val classification: Classification,
    )

    override fun onCleared() {
        // Deliberately do NOT close classifier here: it is the app-lifetime singleton owned
        // by ClassifierBootstrap (see AppGraph.classifier / scanViewModel()), not something this
        // VM owns. On iOS (post-i2c) Scan is a pushed nav destination, so back-out clears this
        // VM on every re-entry; closing the shared classifier here bricked scan AND gallery
        // photo-ID until app relaunch (every later classify() hit the runner's `check(!closed)`
        // guard) — found in i2c final review. The bootstrap, not the VM, decides when the
        // classifier dies.
        // cameraSource.stop() IS per-VM (each ScanViewModel owns its own CameraSource instance,
        // see cameraSourceFactory above) and suspend (CameraX bind/unbind goes via the
        // camera-executor), so we have to dispatch it; viewModelScope is already cancelled here.
        @Suppress("OPT_IN_USAGE")
        GlobalScope.launch(kotlinx.coroutines.Dispatchers.Default + NonCancellable) {
            runCatching { cameraSource.stop() }
        }
    }
}
