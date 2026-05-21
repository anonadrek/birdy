package se.birdy.ml

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

class AndroidAudioRecorder(
    val sampleRate: Int = 48_000,
    val durationMs: Int = 3_000,
) {
    val expectedSamples: Int = sampleRate * durationMs / 1000

    @SuppressLint("MissingPermission")
    suspend fun record3s(onLevel: (rms: Float) -> Unit): ShortArray =
        withContext(Dispatchers.IO) {
            val minBuf =
                AudioRecord.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                )
            require(minBuf > 0) { "AudioRecord.getMinBufferSize returned $minBuf" }

            val source = pickAudioSource()
            val recorder =
                AudioRecord(
                    source,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    maxOf(minBuf, expectedSamples * 2),
                )

            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                recorder.release()
                error("AudioRecord failed to initialize (state=${recorder.state})")
            }

            try {
                recorder.startRecording()
                val buffer = ShortArray(expectedSamples)
                var totalRead = 0
                val chunkSize = sampleRate / 30 // ~30 Hz RMS callback

                while (totalRead < expectedSamples) {
                    val toRead = minOf(chunkSize, expectedSamples - totalRead)
                    val read = recorder.read(buffer, totalRead, toRead)
                    if (read <= 0) error("AudioRecord.read returned $read")

                    val rms = computeRms(buffer, totalRead, read)
                    onLevel(rms)
                    totalRead += read
                }
                buffer
            } finally {
                runCatching { recorder.stop() }
                recorder.release()
            }
        }

    private fun pickAudioSource(): Int {
        // UNPROCESSED gives cleanest signal for ML on supported devices; fallback to VOICE_RECOGNITION
        return try {
            MediaRecorder.AudioSource.UNPROCESSED
        } catch (_: Throwable) {
            MediaRecorder.AudioSource.VOICE_RECOGNITION
        }
    }

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
