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
 * Best classification observed so far across all sliding 3s windows during a
 * single recording session. [pcmOffset]..[pcmEnd] marks the PCM-buffer slice
 * that produced this top-1 — used at final-classify time to re-run the model
 * on the winning window for full Disambig-routing data.
 */
data class Top1(
    val speciesId: String,
    val confidence: Float,
    val pcmOffset: Int,
    val pcmEnd: Int,
)
