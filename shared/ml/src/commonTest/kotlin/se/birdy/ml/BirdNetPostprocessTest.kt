package se.birdy.ml

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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

    @Test
    fun nonFiniteScoresAreDropped() {
        // Digital silence (exact zeros, e.g. the mic muted by the system or another app
        // holding it) makes every BirdNET logit NaN. NaN sorted to the top and reached
        // Listen as "Hör: Berguv · 0%", then broke the result's JSON so the session ended on
        // "Kunde inte identifiera ljudet." (Release 1.3.0 Plan 3 Task 7, API 36 emulator).
        val scores = floatArrayOf(Float.NaN, Float.NaN, 0.3f, Float.POSITIVE_INFINITY)
        val result = rankMappedScores(scores, lookup = { "Q$it" })
        assertEquals(listOf("Q2"), result.map { it.speciesId })
    }

    @Test
    fun allNaNScoresGiveEmptyList() {
        val scores = FloatArray(5) { Float.NaN }
        val result = rankMappedScores(scores, lookup = { "Q$it" })
        assertEquals(emptyList(), result)
    }

    @Test
    fun allZeroPcmIsDigitalSilence() {
        assertTrue(ShortArray(48_000).isDigitalSilence())
        assertTrue(ShortArray(0).isDigitalSilence())
        assertFalse(ShortArray(48_000) { if (it == 7) 1 else 0 }.isDigitalSilence())
    }

    @Test
    fun nonFiniteScoresOnRealAudioAreReported() {
        val realAudio = AudioInput(FloatArray(4) { 0.1f }, 48_000, 3_000, rawPcm = ShortArray(4) { 100 })
        val silence = AudioInput(FloatArray(4), 48_000, 3_000, rawPcm = ShortArray(4))
        val withNaN = floatArrayOf(0.2f, Float.NaN, 0.1f)

        val warning = nonFiniteScoreWarning(withNaN, realAudio)
        assertTrue(warning != null && "1 of 3" in warning, "got $warning")
        // Silence makes every logit NaN by itself; the caller reports that as a recording fault.
        assertNull(nonFiniteScoreWarning(withNaN, silence))
        assertNull(nonFiniteScoreWarning(floatArrayOf(0.2f, 0.1f), realAudio))
    }
}
