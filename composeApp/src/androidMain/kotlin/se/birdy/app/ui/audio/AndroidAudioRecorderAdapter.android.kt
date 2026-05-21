package se.birdy.app.ui.audio

import se.birdy.ml.AndroidAudioRecorder

/**
 * Thin shim that adapts [AndroidAudioRecorder] to [AudioRecorderApi].
 * Plan 6b2 T5 — real recording; no stub needed here.
 */
class AndroidAudioRecorderAdapter(
    private val recorder: AndroidAudioRecorder = AndroidAudioRecorder(),
) : AudioRecorderApi {
    override suspend fun record3s(onLevel: (Float) -> Unit): ShortArray = recorder.record3s(onLevel)
}
