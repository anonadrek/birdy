package se.birdy.app.ui.diary

import kotlinx.datetime.Instant
import se.birdy.domain.observation.Observation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Release 1.3.0 Plan 3 Task 7 (device walkthrough, API 36 emulator): a find saved with "Spara som
 * okänd" has no species, so Mina arter showed its line as " · för 2 min sen" (a separator with
 * nothing before it) and a "0%" match, and the detail said "0% säkerhet vid skanning". An unknown
 * find was never scored as anything, so it shows no confidence at all.
 */
class UnknownFindDisplayTest {
    private fun observation(speciesId: String?) =
        Observation(
            id = "obs",
            speciesId = speciesId,
            capturedAt = Instant.parse("2026-10-06T14:00:00Z"),
            savedAt = Instant.parse("2026-10-06T14:00:00Z"),
            photoPath = "",
            note = "",
            confidence = if (speciesId == null) 0f else 0.82f,
            latitude = null,
            longitude = null,
            locationLabel = null,
        )

    @Test
    fun `a known species shows its scientific name before the time`() {
        assertEquals("Parus major · för 5 min sen", lifelistMetaLine("Parus major", "för 5 min sen"))
    }

    @Test
    fun `an unknown find shows only the time with no separator in front`() {
        assertEquals("för 2 min sen", lifelistMetaLine(null, "för 2 min sen"))
        assertEquals("för 2 min sen", lifelistMetaLine("", "för 2 min sen"))
    }

    @Test
    fun `only a find with a species has a confidence to show`() {
        assertTrue(showsConfidence(observation(speciesId = "Q25485")))
        assertFalse(showsConfidence(observation(speciesId = null)))
    }
}
