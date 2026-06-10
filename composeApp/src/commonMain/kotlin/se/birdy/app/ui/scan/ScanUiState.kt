package se.birdy.app.ui.scan

import se.birdy.ml.ClassificationResult

sealed interface ScanUiState {
    data object PermissionRequired : ScanUiState

    data object PermissionDenied : ScanUiState

    data object Idle : ScanUiState

    data class Scanning(
        val top1: ClassificationResult?,
        // Human-readable, locale-resolved species name for top1; null until resolved or
        // when top1 maps to a non-catalog species. The UI shows this, never the raw speciesId.
        val displayName: String?,
        val isThrottled: Boolean,
    ) : ScanUiState

    data class FrozenAt(
        val predictions: List<ClassificationResult>,
        val frameJpegPath: String,
        val timestampMillis: Long,
    ) : ScanUiState

    data class Error(
        val kind: ScanErrorKind,
    ) : ScanUiState
}

enum class ScanErrorKind {
    ClassifierFailed,
}
