package se.birdy.app.ui.audio

sealed interface AudioScanState {
    data object Preparing : AudioScanState

    data class PermissionNeeded(
        val canRequest: Boolean,
    ) : AudioScanState

    data object Idle : AudioScanState

    data class Recording(
        val rms: Float,
        val elapsedMs: Long,
    ) : AudioScanState

    data class Analyzing(
        val rmsFrozen: Float,
    ) : AudioScanState

    data class NavigateToMatch(
        val sourceJson: String,
    ) : AudioScanState

    sealed interface Error : AudioScanState {
        data object PermanentlyDenied : Error

        data object RecordingFailed : Error

        data class BootstrapFailed(
            val cause: String,
        ) : Error
    }
}
