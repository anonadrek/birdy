package se.birdy.app.ui.audio

import java.io.File

/**
 * Stub implementation of [WaveformRendererApi] for Plan 6b2 T5.
 *
 * Both methods create an empty file at the given path and return the path.
 * T6 replaces this with real PNG waveform rendering and Opus encoding.
 *
 * TODO Plan 6b2 T6: real waveform PNG rendering (AudioBitmap → PNG).
 * TODO Plan 6b2 T6: real Opus encoding (PCM → Opus via libopus JNI or MediaCodec).
 */
class AndroidWaveformRendererStub : WaveformRendererApi {
    override suspend fun renderWaveformPng(
        pcm: ShortArray,
        outPath: String,
    ): String {
        File(outPath).parentFile?.mkdirs()
        File(outPath).writeBytes(byteArrayOf()) // empty; T6 replaces
        return outPath
    }

    override suspend fun encodeOpus(
        pcm: ShortArray,
        outPath: String,
    ): String {
        File(outPath).parentFile?.mkdirs()
        File(outPath).writeBytes(byteArrayOf()) // empty; T6 replaces
        return outPath
    }
}
