package se.birdy.app.ui.map

import se.birdy.app.ui.theme.contrastRatio
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * MapTiler's terms want the map attribution "always visible and readable". It sat as dark ink
 * straight on the map, unreadable over the near-black sea (QA 2026-10-07); it now sits on an
 * opaque paper chip, so its contrast doesn't depend on the tiles under it.
 */
class MapAttributionContrastTest {
    @Test
    fun `the chip is opaque so the map never shows through`() {
        assertEquals(1f, MapAttributionChip.alpha)
    }

    @Test
    fun `the attribution clears AA on its chip`() {
        val ratio = contrastRatio(MapAttributionInk, MapAttributionChip)
        assertTrue(ratio >= 4.5, "attribution on its chip: $ratio")
    }
}
