package se.birdy.app.ui.photoanalyze

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import se.birdy.ml.BirdClassifier
import se.birdy.ml.ImageInput

class PhotoAnalyzeViewModel(
    private val classifier: BirdClassifier,
    private val persist: (ByteArray) -> String,
    private val minShortSide: Int = 224,
) : ViewModel() {
    private val _state = MutableStateFlow<PhotoAnalyzeUiState>(PhotoAnalyzeUiState.Idle)
    val state: StateFlow<PhotoAnalyzeUiState> = _state.asStateFlow()

    fun analyze(frame: ImageInput) {
        val shortSide = minOf(frame.widthPx, frame.heightPx)
        if (shortSide < minShortSide) {
            _state.value = PhotoAnalyzeUiState.Error(PhotoAnalyzeUiState.Error.Kind.TooSmall)
            return
        }
        _state.value = PhotoAnalyzeUiState.Analyzing
        viewModelScope.launch {
            val classification =
                runCatching { classifier.classify(frame) }
                    .getOrElse {
                        _state.value =
                            PhotoAnalyzeUiState.Error(
                                PhotoAnalyzeUiState.Error.Kind.ClassifierFailure,
                            )
                        return@launch
                    }
            val path =
                runCatching { persist(frame.bytes) }
                    .getOrElse {
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
                )
        }
    }

    fun reset() {
        _state.value = PhotoAnalyzeUiState.Idle
    }
}
