package se.birdy.app.ui.encyclopedia

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArchiveChipTest {
    @Test
    fun `ALL matches any group and has empty key`() {
        assertEquals("", ArchiveChip.ALL.key)
        assertTrue(ArchiveChip.ALL.matches("auks"))
        assertTrue(ArchiveChip.ALL.matches(""))
    }

    @Test
    fun `non-ALL chips match only their own group key`() {
        assertTrue(ArchiveChip.AUKS.matches("auks"))
        assertFalse(ArchiveChip.AUKS.matches("songbirds"))
        assertTrue(ArchiveChip.WADERS.matches("waders"))
        assertFalse(ArchiveChip.WADERS.matches("auks"))
        assertTrue(ArchiveChip.WOODPECKERS.matches("woodpeckers"))
    }

    @Test
    fun `chip keys match the locked content group ids and are unique`() {
        val expected =
            setOf(
                "songbirds",
                "waterfowl",
                "waders",
                "gulls_terns",
                "auks",
                "seabirds",
                "grebes_divers",
                "herons_storks",
                "raptors",
                "owls",
                "gamebirds",
                "doves",
                "woodpeckers",
                "cranes_rails",
                "other",
            )
        val keys = ArchiveChip.entries.filter { it != ArchiveChip.ALL }.map { it.key }
        assertEquals(expected.size, keys.size, "dubbletter eller saknade chip-nycklar")
        assertEquals(expected, keys.toSet())
    }
}
