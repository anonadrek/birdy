package se.birdy.app.ui.audio

import se.birdy.ml.AndroidAudioRecorder

/**
 * Thin shim that adapts [AndroidAudioRecorder] to [AudioRecorderApi].
 * Plan 6b2 T5 — real recording; streaming impl wired in Task 3.
 *
 * TODO(listen/t3): implement streaming [start] backed by [AndroidAudioRecorder].
 *   The old [AndroidAudioRecorder.record3s] API will be replaced with a
 *   chunk-emitting loop that calls [onChunk] ~30 times/sec.
 */
class AndroidAudioRecorderAdapter(
    private val recorder: AndroidAudioRecorder = AndroidAudioRecorder(),
) : AudioRecorderApi {
    override fun start(
        onChunk: (samples: ShortArray, rms: Float, totalSamplesSoFar: Int) -> Unit,
        onCapReached: () -> Unit,
        maxDurationMs: Long,
    ): RecorderHandle = TODO("Streaming AndroidAudioRecorderAdapter implemented in Task 3")
}
