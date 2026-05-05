package se.birdy.app.ui.scan

import se.birdy.ml.ClassificationResult

sealed interface ScanUiState {
    data object PermissionRequired : ScanUiState

    data object PermissionDenied : ScanUiState

    data object Idle : ScanUiState

    data class Scanning(
        val top1: ClassificationResult?,
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
