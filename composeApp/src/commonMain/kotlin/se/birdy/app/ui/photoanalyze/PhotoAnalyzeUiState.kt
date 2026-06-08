package se.birdy.app.ui.photoanalyze

import se.birdy.ml.ClassificationResult
import se.birdy.ml.ImageOrigin

sealed interface PhotoAnalyzeUiState {
    data object Idle : PhotoAnalyzeUiState

    data object Analyzing : PhotoAnalyzeUiState

    data class Loaded(
        val predictions: List<ClassificationResult>,
        val frameJpegPath: String,
        val capturedAtMs: Long,
        val origin: ImageOrigin = ImageOrigin.Gallery,
        val exifLatitude: Double? = null,
        val exifLongitude: Double? = null,
    ) : PhotoAnalyzeUiState

    data class Error(
        val kind: Kind,
    ) : PhotoAnalyzeUiState {
        enum class Kind { TooSmall, DecodeFailure, ClassifierFailure, IoFailure }
    }
}
