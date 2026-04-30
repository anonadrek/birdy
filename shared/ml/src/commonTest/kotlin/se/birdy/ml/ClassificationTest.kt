package se.birdy.ml

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClassificationTest {
    @Test
    fun classification_top_returns_highest_confidence_entry() {
        val classification =
            Classification(
                results =
                    listOf(
                        ClassificationResult(speciesId = "parus_major", confidence = 0.6f),
                        ClassificationResult(speciesId = "cyanistes_caeruleus", confidence = 0.85f),
                        ClassificationResult(speciesId = "passer_domesticus", confidence = 0.3f),
                    ),
            )
        assertEquals("cyanistes_caeruleus", classification.top()?.speciesId)
    }

    @Test
    fun classification_top_returns_null_for_empty_results() {
        val classification = Classification(results = emptyList())
        assertEquals(null, classification.top())
    }

    @Test
    fun classification_results_sorted_descending_by_confidence() {
        val classification =
            Classification(
                results =
                    listOf(
                        ClassificationResult("a", 0.1f),
                        ClassificationResult("b", 0.9f),
                        ClassificationResult("c", 0.5f),
                    ),
            )
        val sorted = classification.sortedByConfidenceDescending()
        assertEquals(listOf("b", "c", "a"), sorted.map { it.speciesId })
        assertTrue(sorted[0].confidence >= sorted[1].confidence)
        assertTrue(sorted[1].confidence >= sorted[2].confidence)
    }
}
