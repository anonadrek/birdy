package se.birdy.app.ui.audio

import se.birdy.ml.AndroidAudioRecorder

/**
 * Bridges [AndroidAudioRecorder] (Android-platform layer) to [AudioRecorderApi]
 * (common audio-scan layer). Open-ended capture replaces old fixed-3s flow.
 */
class AndroidAudioRecorderAdapter(
    private val recorder: AndroidAudioRecorder = AndroidAudioRecorder(),
) : AudioRecorderApi {
    override fun start(
        onChunk: (samples: ShortArray, rms: Float, totalSamplesSoFar: Int) -> Unit,
        onCapReached: () -> Unit,
        maxDurationMs: Long,
    ): RecorderHandle {
        val androidHandle = recorder.start(onChunk, onCapReached, maxDurationMs)
        return object : RecorderHandle {
            override suspend fun stopAndFlush(): ShortArray = androidHandle.stopAndFlush()

            override fun cancel() = androidHandle.cancel()
        }
    }
}
