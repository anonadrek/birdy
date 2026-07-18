package se.birdy.app.ui.photoanalyze

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import se.birdy.ml.BirdClassifier
import se.birdy.ml.ImageInput

class PhotoAnalyzeViewModel(
    private val classifier: BirdClassifier,
    private val persist: (ByteArray) -> String,
    private val minShortSide: Int = 224,
    private val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : ViewModel() {
    private val _state = MutableStateFlow<PhotoAnalyzeUiState>(PhotoAnalyzeUiState.Idle)
    val state: StateFlow<PhotoAnalyzeUiState> = _state.asStateFlow()

    fun analyze(frame: ImageInput) {
        val shortSide = minOf(frame.widthPx, frame.heightPx)
        if (shortSide < minShortSide) {
            _state.value = PhotoAnalyzeUiState.Error(PhotoAnalyzeUiState.Error.Kind.TooSmall)
            return
        }
        val capturedAtMs = clock()
        _state.value = PhotoAnalyzeUiState.Analyzing
        viewModelScope.launch {
            val classification =
                runCatching { classifier.classify(frame) }
                    .onFailure {
                        if (it is CancellationException) throw it
                        // The UI only shows a generic ClassifierFailure; without this we are
                        // blind to the real exception in the field (first-run bug, i2a).
                        println("PhotoAnalyzeViewModel: classify failed:\n${it.stackTraceToString()}")
                    }.getOrElse {
                        _state.value =
                            PhotoAnalyzeUiState.Error(
                                PhotoAnalyzeUiState.Error.Kind.ClassifierFailure,
                            )
                        return@launch
                    }
            val path =
                runCatching { persist(frame.bytes) }
                    .onFailure {
                        if (it is CancellationException) throw it
                        println("PhotoAnalyzeViewModel: persist failed:\n${it.stackTraceToString()}")
                    }.getOrElse {
                        _state.value =
                            PhotoAnalyzeUiState.Error(
                                PhotoAnalyzeUiState.Error.Kind.IoFailure,
                            )
                        return@launch
                    }
            _state.value =
                PhotoAnalyzeUiState.Loaded(
                    predictions = classification.sortedByConfidenceDescending(),
                    frameJpegPath = path,
                    capturedAtMs = capturedAtMs,
                )
        }
    }

    fun markAnalyzing() {
        _state.value = PhotoAnalyzeUiState.Analyzing
    }

    fun decodeFailed() {
        _state.value = PhotoAnalyzeUiState.Error(PhotoAnalyzeUiState.Error.Kind.DecodeFailure)
    }

    fun reset() {
        _state.value = PhotoAnalyzeUiState.Idle
    }
}
