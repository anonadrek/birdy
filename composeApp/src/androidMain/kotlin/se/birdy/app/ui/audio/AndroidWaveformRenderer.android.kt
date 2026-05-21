package se.birdy.app.ui.audio

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * Real implementation of [WaveformRendererApi] for Plan 6b2 T6.
 *
 * [renderWaveformPng]: renders PCM samples as a waveform bar chart onto a 600×200 Bitmap
 * using the Mossbädd colour palette (PaperBg background, MarginaliaInk bars,
 * AccentCopper underline). Output is written as a lossless PNG.
 *
 * [encodeOpus]: encodes a mono 48 kHz PCM ShortArray into an Opus-in-OGG container
 * via [MediaCodec] + [MediaMuxer] at ~32 kbps. Output is written to [outPath].
 */
class AndroidWaveformRenderer : WaveformRendererApi {
    override suspend fun renderWaveformPng(
        pcm: ShortArray,
        outPath: String,
    ): String =
        withContext(Dispatchers.Default) {
            val width = 600
            val height = 200
            val buckets = 120
            val samplesPerBucket = if (pcm.isNotEmpty()) pcm.size / buckets else 1

            val peaks =
                FloatArray(buckets) { b ->
                    var max = 0
                    val start = b * samplesPerBucket
                    val end = (start + samplesPerBucket).coerceAtMost(pcm.size)
                    for (i in start until end) {
                        val v = abs(pcm[i].toInt())
                        if (v > max) max = v
                    }
                    max / 32768f
                }

            // 3-tap smoothing
            val smoothed =
                FloatArray(buckets) { i ->
                    val prev = peaks[(i - 1).coerceAtLeast(0)]
                    val cur = peaks[i]
                    val next = peaks[(i + 1).coerceAtMost(buckets - 1)]
                    (prev + cur + next) / 3f
                }

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.parseColor("#EFE7D6")) // PaperBg

                val barPaint =
                    Paint().apply {
                        color = Color.parseColor("#3F4F30") // MarginaliaInk
                        style = Paint.Style.FILL
                        isAntiAlias = true
                    }
                val underlinePaint =
                    Paint().apply {
                        color = Color.parseColor("#A8552D") // AccentCopper
                        style = Paint.Style.STROKE
                        strokeWidth = 2f
                        isAntiAlias = true
                    }

                val barWidth = width.toFloat() / buckets
                val centerY = height / 2f
                val maxHalfHeight = height * 0.4f
                smoothed.forEachIndexed { i, level ->
                    val h = (maxHalfHeight * level).coerceAtLeast(2f)
                    val x = i * barWidth
                    canvas.drawRect(x + 1f, centerY - h, x + barWidth - 1f, centerY + h, barPaint)
                }
                canvas.drawLine(0f, height - 6f, width.toFloat(), height - 6f, underlinePaint)

                val file = File(outPath)
                file.parentFile?.mkdirs()
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                outPath
            } finally {
                bitmap.recycle()
            }
        }

    override suspend fun encodeOpus(
        pcm: ShortArray,
        outPath: String,
    ): String =
        withContext(Dispatchers.Default) {
            val sampleRate = 48_000
            val file = File(outPath)
            file.parentFile?.mkdirs()

            val format =
                MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_OPUS, sampleRate, 1).apply {
                    setInteger(MediaFormat.KEY_BIT_RATE, 32_000)
                }
            val codec =
                MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS).apply {
                    configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                    start()
                }
            val muxer = MediaMuxer(outPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG)
            var muxerStarted = false
            try {
                val pcmBytes =
                    ByteBuffer
                        .allocate(pcm.size * 2)
                        .apply {
                            order(ByteOrder.LITTLE_ENDIAN)
                            pcm.forEach { putShort(it) }
                        }.array()

                var trackIdx = -1
                var inputOffset = 0
                var sawEos = false
                val bufferInfo = MediaCodec.BufferInfo()

                while (!sawEos) {
                    currentCoroutineContext().ensureActive()
                    val inIdx = codec.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val inputBuf = codec.getInputBuffer(inIdx)!!
                        inputBuf.clear()
                        val remaining = pcmBytes.size - inputOffset
                        if (remaining <= 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        } else {
                            val chunk = remaining.coerceAtMost(inputBuf.capacity())
                            inputBuf.put(pcmBytes, inputOffset, chunk)
                            val presUs = inputOffset.toLong() * 1_000_000L / 2L / sampleRate
                            codec.queueInputBuffer(inIdx, 0, chunk, presUs, 0)
                            inputOffset += chunk
                        }
                    }
                    val outIdx = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                    when {
                        outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            trackIdx = muxer.addTrack(codec.outputFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                        outIdx >= 0 -> {
                            val out = codec.getOutputBuffer(outIdx)!!
                            if (muxerStarted && bufferInfo.size > 0) {
                                muxer.writeSampleData(trackIdx, out, bufferInfo)
                            }
                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                sawEos = true
                            }
                            codec.releaseOutputBuffer(outIdx, false)
                        }
                    }
                }
                outPath
            } finally {
                runCatching { codec.stop() }
                runCatching { codec.release() }
                if (muxerStarted) runCatching { muxer.stop() }
                runCatching { muxer.release() }
            }
        }
}
