package se.birdy.ml

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BirdNetPostprocessTest {
    @Test
    fun flatSigmoidMapsLogitsToConfidences() {
        assertEquals(0.5f, flatSigmoid(0f), 1e-6f)
        // Klipp vid ±15: värden utanför ger exakt samma resultat som gränsen.
        assertEquals(flatSigmoid(15f), flatSigmoid(99f), 0f)
        assertEquals(flatSigmoid(-15f), flatSigmoid(-99f), 0f)
        // Monotont stigande + rimliga ändpunkter.
        assertTrue(flatSigmoid(-15f) < 1e-6f)
        assertTrue(flatSigmoid(15f) > 0.999999f)
        assertTrue(flatSigmoid(1f) > flatSigmoid(0f))
    }

    @Test
    fun unmappedTopClassesDoNotCrowdOutMappedSpecies() {
        // Index 0-2 (omappade brusklasser) har högst score; den mappade arten
        // på råplats 4 ska ändå vinna. Detta var den shippade buggen:
        // take(3)-före-filter gav tom lista här.
        val scores = floatArrayOf(0.9f, 0.8f, 0.7f, 0.6f, 0.3f, 0.2f)
        val mapping = mapOf(3 to "QA", 4 to "QB", 5 to "QC")
        val result = rankMappedScores(scores, lookup = { mapping[it] })
        assertEquals(listOf("QA", "QB", "QC"), result.map { it.speciesId })
        assertEquals(0.6f, result[0].confidence)
    }

    @Test
    fun takesAtMostThreeByDefault() {
        val scores = floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f, 0.5f)
        val result = rankMappedScores(scores, lookup = { "Q$it" })
        assertEquals(3, result.size)
        assertEquals(listOf("Q4", "Q3", "Q2"), result.map { it.speciesId })
    }

    @Test
    fun allUnmappedGivesEmptyList() {
        val result = rankMappedScores(floatArrayOf(0.9f, 0.8f), lookup = { null })
        assertEquals(emptyList(), result)
    }
}
