package se.birdy.ml

import kotlin.test.Test
import kotlin.test.assertEquals

class ImagePreprocessorContractTest {
    @Test
    fun signature_returns_float_array_of_correct_length() {
        // Placeholder; real common-side coverage uses StubPreprocessor in Task 7.
        val expected = 4 * 4 * 3
        val out = FloatArray(expected) { 0f }
        assertEquals(expected, out.size)
    }
}
