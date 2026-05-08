package se.birdy.app.ui.theme

import kotlin.test.Test
import kotlin.test.assertNotNull

class FontsTest {
    @Test
    fun `dm serif display family is registered`() {
        // Sanity check att resource-IDs finns.
        val familyId = "Res.font.dm_serif_display_regular"
        assertNotNull(familyId)
    }
}
