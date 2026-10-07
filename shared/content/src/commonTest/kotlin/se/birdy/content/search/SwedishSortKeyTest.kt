package se.birdy.content.search

import kotlin.test.Test
import kotlin.test.assertEquals

class SwedishSortKeyTest {
    @Test
    fun `the swedish letters come after z in their own order`() {
        assertEquals(
            listOf("Zeta", "Ålgräsfink", "Ärtsångare", "Öknlärka"),
            listOf("Öknlärka", "Ärtsångare", "Zeta", "Ålgräsfink").sortedBy(::swedishSortKey),
        )
    }

    @Test
    fun `other accents sort with their base letter and case does not matter`() {
        assertEquals(
            listOf("ekorre", "Émile", "Eva", "Rüppellgam", "Rödhake"),
            listOf("Rödhake", "Eva", "Rüppellgam", "Émile", "ekorre").sortedBy(::swedishSortKey),
        )
    }

    @Test
    fun `danish and norwegian letters sort as their swedish twins`() {
        assertEquals(swedishSortKey("ä"), swedishSortKey("æ"))
        assertEquals(swedishSortKey("ö"), swedishSortKey("ø"))
    }
}
