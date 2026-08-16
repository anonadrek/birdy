package se.birdy.ml

import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PcmChunkerTest {
    private class Sink {
        val chunks = mutableListOf<ShortArray>()
        val rms = mutableListOf<Float>()
        val totals = mutableListOf<Int>()
        var caps = 0
    }

    private fun chunker(
        sink: Sink,
        chunkSize: Int = 4,
        maxSamples: Int = 20,
    ) = PcmChunker(
        chunkSize = chunkSize,
        maxSamples = maxSamples,
        onChunk = { s, r, t ->
            sink.chunks += s
            sink.rms += r
            sink.totals += t
        },
        onCapReached = { sink.caps++ },
    )

    @Test
    fun emitsFixedSizeChunksAcrossUnevenBuffers() {
        val sink = Sink()
        val c = chunker(sink)
        c.accept(shortArrayOf(1, 2, 3)) // 3 pending — inget chunk än
        c.accept(shortArrayOf(4, 5, 6, 7, 8, 9)) // 9 totalt → två 4-chunks, 1 pending
        assertEquals(2, sink.chunks.size)
        assertContentEquals(shortArrayOf(1, 2, 3, 4), sink.chunks[0])
        assertContentEquals(shortArrayOf(5, 6, 7, 8), sink.chunks[1])
        assertEquals(listOf(4, 8), sink.totals)
    }

    @Test
    fun rmsMatchesAndroidFormula() {
        val sink = Sink()
        val c = chunker(sink, chunkSize = 2, maxSamples = 100)
        c.accept(shortArrayOf(16384, -16384)) // |0.5| → rms = 0.5
        assertEquals(1, sink.chunks.size)
        val expected = sqrt((0.5 * 0.5 + 0.5 * 0.5) / 2).toFloat()
        assertEquals(expected, sink.rms[0], 1e-4f)
    }

    @Test
    fun capFiresOnceAndStopsAccumulating() {
        val sink = Sink()
        val c = chunker(sink, chunkSize = 4, maxSamples = 8)
        c.accept(ShortArray(6) { 1 })
        c.accept(ShortArray(6) { 2 }) // passerar cap vid 8
        c.accept(ShortArray(6) { 3 }) // efter cap — ignoreras
        assertEquals(1, sink.caps)
        assertEquals(8, c.snapshot().size)
        assertTrue(sink.totals.all { it <= 8 })
    }

    @Test
    fun snapshotReturnsExactlyCapturedSamples() {
        val sink = Sink()
        val c = chunker(sink, chunkSize = 4, maxSamples = 100)
        c.accept(shortArrayOf(7, 8, 9))
        assertContentEquals(shortArrayOf(7, 8, 9), c.snapshot())
    }
}
