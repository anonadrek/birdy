package se.birdy.app.ui.audio

sealed interface AudioScanState {
    object Preparing : AudioScanState

    data class PermissionNeeded(
        val canRequest: Boolean,
    ) : AudioScanState

    object Idle : AudioScanState

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
        object PermanentlyDenied : Error

        object RecordingFailed : Error

        data class BootstrapFailed(
            val cause: String,
        ) : Error
    }
}
