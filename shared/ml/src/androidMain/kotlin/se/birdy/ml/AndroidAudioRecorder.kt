package se.birdy.ml

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

/**
 * Captures open-ended 48 kHz mono PCM_16BIT audio via AudioRecord.
 *
 * Caller MUST hold the `android.permission.RECORD_AUDIO` permission before
 * invoking [start] — this class does NOT prompt the user.
 *
 * Capture stops when either:
 * - [AndroidRecorderHandle.stopAndFlush] is called by consumer
 * - [AndroidRecorderHandle.cancel] is called
 * - `maxDurationMs` elapses (then `onCapReached` fires and capture stops)
 */
class AndroidAudioRecorder(
    val sampleRate: Int = 48_000,
) {
    @SuppressLint("MissingPermission")
    fun start(
        onChunk: (samples: ShortArray, rms: Float, totalSamplesSoFar: Int) -> Unit,
        onCapReached: () -> Unit,
        maxDurationMs: Long = 60_000L,
    ): AndroidRecorderHandle {
        val maxSamples = (sampleRate * maxDurationMs / 1000L).toInt()
        val minBuf =
            AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
        require(minBuf > 0) { "AudioRecord.getMinBufferSize returned $minBuf" }

        var recorder = buildRecorder(MediaRecorder.AudioSource.UNPROCESSED, minBuf)
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            recorder = buildRecorder(MediaRecorder.AudioSource.VOICE_RECOGNITION, minBuf)
        }
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            error("AudioRecord failed to initialize with either UNPROCESSED or VOICE_RECOGNITION")
        }

        val captured = ShortArray(maxSamples)
        var total = 0

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val stopRequested = CompletableDeferred<Unit>()
        val cancelRequested = CompletableDeferred<Unit>()

        val job: Job =
            scope.launch {
                try {
                    recorder.startRecording()
                    val chunkSize = sampleRate / 30 // ~33ms
                    val chunkBuf = ShortArray(chunkSize)
                    while (total < maxSamples && !stopRequested.isCompleted && !cancelRequested.isCompleted) {
                        val toRead = minOf(chunkSize, maxSamples - total)
                        val read = recorder.read(chunkBuf, 0, toRead)
                        if (read <= 0) break
                        chunkBuf.copyInto(captured, destinationOffset = total, startIndex = 0, endIndex = read)
                        val rms = computeRms(chunkBuf, 0, read)
                        total += read
                        if (!cancelRequested.isCompleted) {
                            onChunk(chunkBuf.copyOf(read), rms, total)
                        }
                    }
                    if (total >= maxSamples && !stopRequested.isCompleted && !cancelRequested.isCompleted) {
                        onCapReached()
                    }
                } finally {
                    runCatching { recorder.stop() }
                    recorder.release()
                }
            }

        return AndroidRecorderHandle(
            stopRequested = stopRequested,
            cancelRequested = cancelRequested,
            job = job,
            scope = scope,
            getCaptured = { captured.copyOf(total) },
        )
    }

    @SuppressLint("MissingPermission")
    private fun buildRecorder(
        source: Int,
        bufBytes: Int,
    ): AudioRecord =
        AudioRecord(
            source,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufBytes,
        )

    private fun computeRms(
        buffer: ShortArray,
        offset: Int,
        length: Int,
    ): Float {
        if (length == 0) return 0f
        var sum = 0.0
        for (i in offset until offset + length) {
            val s = buffer[i] / 32768.0
            sum += s * s
        }
        return sqrt(sum / length).toFloat().coerceIn(0f, 1f)
    }
}

class AndroidRecorderHandle internal constructor(
    private val stopRequested: CompletableDeferred<Unit>,
    private val cancelRequested: CompletableDeferred<Unit>,
    private val job: Job,
    private val scope: CoroutineScope,
    private val getCaptured: () -> ShortArray,
) {
    suspend fun stopAndFlush(): ShortArray =
        withContext(Dispatchers.IO) {
            stopRequested.complete(Unit)
            job.join()
            scope.cancel()
            getCaptured()
        }

    fun cancel() {
        cancelRequested.complete(Unit)
        scope.cancel()
    }
}
