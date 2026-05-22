package se.birdy.app.ui.audio

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.min

/**
 * Test-double for [AudioRecorderApi] that emits pre-canned PCM chunks at
 * controllable cadence. Drives by [emitChunks] — each call delivers
 * [chunkSize] samples and advances [totalSamples].
 */
class FakeStreamingRecorder(
    val chunkSize: Int = 1_600,           // ~33ms @ 48kHz
    val chunkRms: Float = 0.5f,
    val maxBufferSamples: Int = 60 * 48_000,
) : AudioRecorderApi {
    private var onChunk: ((ShortArray, Float, Int) -> Unit)? = null
    private var onCap: (() -> Unit)? = null
    private val buffer = ShortArray(maxBufferSamples)
    private var totalSamples = 0
    private val stopped = MutableStateFlow(false)
    private val cancelled = MutableStateFlow(false)

    override fun start(
        onChunk: (samples: ShortArray, rms: Float, totalSamplesSoFar: Int) -> Unit,
        onCapReached: () -> Unit,
        maxDurationMs: Long,
    ): RecorderHandle {
        this.onChunk = onChunk
        this.onCap = onCapReached
        return object : RecorderHandle {
            override suspend fun stopAndFlush(): ShortArray {
                stopped.value = true
                return buffer.copyOf(totalSamples)
            }

            override fun cancel() {
                cancelled.value = true
            }
        }
    }

    /**
     * Drives the recorder synchronously: emits [count] chunks of [chunkSize]
     * samples each, calling onChunk with monotonically increasing totalSamples.
     * Stops early if [RecorderHandle.stopAndFlush] or [RecorderHandle.cancel]
     * has been called.
     */
    suspend fun emitChunks(count: Int) {
        repeat(count) {
            if (stopped.value || cancelled.value) return
            val sliceStart = totalSamples
            val sliceEnd = min(sliceStart + chunkSize, maxBufferSamples)
            val len = sliceEnd - sliceStart
            if (len <= 0) {
                onCap?.invoke()
                return
            }
            // Leave buffer at default zeros — silence; tests don't read amplitude.
            totalSamples = sliceEnd
            onChunk?.invoke(ShortArray(len), chunkRms, totalSamples)
            delay(33)  // ~realtime cadence; test scheduler advances virtually
            if (totalSamples == maxBufferSamples) {
                onCap?.invoke()
                return
            }
        }
    }

    fun snapshotTotalSamples(): Int = totalSamples
}
