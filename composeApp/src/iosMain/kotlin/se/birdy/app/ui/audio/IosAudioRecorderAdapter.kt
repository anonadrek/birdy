package se.birdy.app.ui.audio

import se.birdy.ml.IosAudioRecorder

/** iOS-spegel av [AndroidAudioRecorderAdapter]: bryggar [IosAudioRecorder] till [AudioRecorderApi]. */
class IosAudioRecorderAdapter(
    private val recorder: IosAudioRecorder = IosAudioRecorder(),
) : AudioRecorderApi {
    override fun start(
        onChunk: (samples: ShortArray, rms: Float, totalSamplesSoFar: Int) -> Unit,
        onCapReached: () -> Unit,
        onError: (Throwable) -> Unit,
        maxDurationMs: Long,
    ): RecorderHandle {
        val iosHandle = recorder.start(onChunk, onCapReached, onError, maxDurationMs)
        return object : RecorderHandle {
            override suspend fun stopAndFlush(): ShortArray = iosHandle.stopAndFlush()

            override fun cancel() = iosHandle.cancel()
        }
    }
}
