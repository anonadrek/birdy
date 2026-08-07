package se.birdy.app.ui.audio

sealed interface AudioScanState {
    data object Preparing : AudioScanState

    data object PermissionNeeded : AudioScanState

    data object Idle : AudioScanState

    data class Recording(
        val rms: Float,
        val elapsedMs: Long,
        val bestSoFar: Top1? = null,
    ) : AudioScanState

    data class Analyzing(
        val rmsFrozen: Float,
    ) : AudioScanState

    data class NavigateToMatch(
        val sourceJson: String,
        val capturedAtMs: Long,
    ) : AudioScanState

    sealed interface Error : AudioScanState {
        data object PermanentlyDenied : Error

        data object RecordingFailed : Error

        data class BootstrapFailed(
            val cause: String,
        ) : Error
    }
}

/**
 * Per-species best confidence observed across all sliding 3s windows during a
 * single recording session. Entries live in the session accumulator
 * (`AudioScanViewModel.sessionScores`); the top entry drives
 * `Recording.bestSoFar` (the live "current best guess" shown while
 * recording), and at finalize time the whole accumulator is ranked into the
 * top-3 list packed into `ScanSource`.
 */
data class Top1(
    val speciesId: String,
    val confidence: Float,
)
