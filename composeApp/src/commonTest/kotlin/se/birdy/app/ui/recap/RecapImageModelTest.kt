package se.birdy.app.ui.recap

import kotlinx.datetime.LocalDate
import se.birdy.app.util.speciesImageUri
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Which photo a find shows in the weekly recap (release 1.3.0 Task 7j): the user's own photo, but
 * for a heard find the species' plate photo first, since its own image is the recording's waveform.
 */
class RecapImageModelTest {
    private fun find(
        photoPath: String,
        heroImagePath: String?,
        isHeard: Boolean,
    ) = RecapFindItem(
        observationId = "o",
        speciesName = "Talgoxe",
        photoPath = photoPath,
        heroImagePath = heroImagePath,
        date = LocalDate(2026, 10, 7),
        isHeard = isHeard,
    )

    @Test
    fun `a heard find shows the plate photo`() {
        assertEquals(
            speciesImageUri("Q25485/hero.webp"),
            recapImageModel(find("/audio/1.png", "Q25485/hero.webp", isHeard = true)),
        )
    }

    @Test
    fun `a heard find without a plate photo falls back to its waveform`() {
        assertEquals("file:///audio/1.png", recapImageModel(find("/audio/1.png", null, isHeard = true)))
    }

    @Test
    fun `a photo find shows the user's own photo`() {
        assertEquals("file:///photos/1.jpg", recapImageModel(find("/photos/1.jpg", "Q25485/hero.webp", isHeard = false)))
    }

    @Test
    fun `a photo find with a blank path shows the plate photo`() {
        assertEquals(speciesImageUri("Q25485/hero.webp"), recapImageModel(find("  ", "Q25485/hero.webp", isHeard = false)))
    }

    @Test
    fun `a find with neither photo shows only its initial`() {
        assertNull(recapImageModel(find("", null, isHeard = false)))
    }
}
