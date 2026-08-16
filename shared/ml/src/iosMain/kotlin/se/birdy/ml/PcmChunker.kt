package se.birdy.ml

import kotlin.math.sqrt

/**
 * Ren, trådlös ackumulator för [IosAudioRecorder]: samlar inkommande PCM-buffertar
 * (godtycklig längd från AVAudioConverter) och emitterar fasta [chunkSize]-chunks med
 * rms + ackumulerad total — samma kadens/format som [AndroidAudioRecorder]s capture-loop
 * (~33 ms à sampleRate/30). Slutar ackumulera vid [maxSamples] och fyrar [onCapReached]
 * EN gång. Trådsäkerhet ägs av anroparen ([IosAudioRecorder] serialiserar via lås).
 */
internal class PcmChunker(
    private val chunkSize: Int,
    private val maxSamples: Int,
    private val onChunk: (samples: ShortArray, rms: Float, totalSamplesSoFar: Int) -> Unit,
    private val onCapReached: () -> Unit,
) {
    private val captured = ShortArray(maxSamples)
    private var total = 0
    private var pendingStart = 0 // index i captured där nästa oemittade chunk börjar
    private var capFired = false

    fun accept(samples: ShortArray) {
        if (capFired) return
        val toCopy = minOf(samples.size, maxSamples - total)
        if (toCopy > 0) {
            samples.copyInto(captured, destinationOffset = total, startIndex = 0, endIndex = toCopy)
            total += toCopy
        }
        while (total - pendingStart >= chunkSize) {
            val chunk = captured.copyOfRange(pendingStart, pendingStart + chunkSize)
            pendingStart += chunkSize
            onChunk(chunk, computeRms(chunk), pendingStart)
        }
        if (total >= maxSamples && !capFired) {
            capFired = true
            onCapReached()
        }
    }

    fun snapshot(): ShortArray = captured.copyOf(total)

    private fun computeRms(buffer: ShortArray): Float {
        if (buffer.isEmpty()) return 0f
        var sum = 0.0
        for (s in buffer) {
            val v = s / 32768.0
            sum += v * v
        }
        return sqrt(sum / buffer.size).toFloat().coerceIn(0f, 1f)
    }
}
