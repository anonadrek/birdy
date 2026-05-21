package se.birdy.ml

import kotlin.test.Test
import kotlin.test.assertEquals

class AudioPreprocessorTest {
    @Test
    fun normalize_dividesBy32768() {
        val input = shortArrayOf(32_767, 0, -32_768, 16_384)
        val out = normalize(input)
        assertEquals(0.999969f, out[0], 0.0001f)
        assertEquals(0f, out[1])
        assertEquals(-1f, out[2])
        assertEquals(0.5f, out[3], 0.0001f)
    }

    @Test
    fun normalize_preservesLength() {
        val input = ShortArray(144_000) { (it % 100).toShort() }
        val out = normalize(input)
        assertEquals(144_000, out.size)
    }
}
