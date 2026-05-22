package se.birdy.pdf

import kotlin.test.Test
import kotlin.test.assertEquals

class JournalPdfInputTest {
    @Test
    fun construct_empty_input_has_zero_observations() {
        val input =
            JournalPdfInput(
                displayName = "Albin",
                generatedAtMs = 1716220800000L,
                observations = emptyList(),
                speciesByQid = emptyMap(),
                stats =
                    JournalPdfInput.Stats(
                        speciesSeenThisYear = 0,
                        totalObservationsThisYear = 0,
                        topSpecies = emptyList(),
                    ),
                unlockedPremiumBadges = emptyList(),
            )
        assertEquals(0, input.observations.size)
    }
}
