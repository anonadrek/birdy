package se.birdy.content

import kotlin.test.Test
import kotlin.test.assertEquals

class SpeciesTextCleanerTest {
    @Test
    fun `strips a leading hash heading and blank line`() {
        val raw = "# Talgoxe\n\nTalgoxen är en av våra vanligaste fåglar."
        assertEquals("Talgoxen är en av våra vanligaste fåglar.", cleanSpeciesText(raw))
    }

    @Test
    fun `strips a leading hash heading with a dash subtitle`() {
        val raw = "# Talgoxe – Förekomst i Skandinavien och Nordeuropa\n\nTexten om förekomst."
        assertEquals("Texten om förekomst.", cleanSpeciesText(raw))
    }

    @Test
    fun `strips a leading bold only heading line`() {
        val raw = "**Grågam - Förekomst i Norden**\n\nGrågamen häckar i bergstrakter."
        assertEquals("Grågamen häckar i bergstrakter.", cleanSpeciesText(raw))
    }

    @Test
    fun `strips a short leading bold only heading line`() {
        val raw = "**Kalanderlärka**\n\nKalanderlärkan trivs på öppna fält."
        assertEquals("Kalanderlärkan trivs på öppna fält.", cleanSpeciesText(raw))
    }

    @Test
    fun `removes inline bold markers but keeps the words`() {
        assertEquals("a b c", cleanSpeciesText("a **b** c"))
    }

    @Test
    fun `removes single star emphasis around a scientific name`() {
        assertEquals(
            "The Circaetus gallicus eagle",
            cleanSpeciesText("The *Circaetus gallicus* eagle"),
        )
    }

    @Test
    fun `leaves already clean text unchanged`() {
        val text = "Talgoxen är en allmän art i hela landet."
        assertEquals(text, cleanSpeciesText(text))
    }

    @Test
    fun `a lone leading hash with no title collapses to empty`() {
        assertEquals("", cleanSpeciesText("#"))
    }

    @Test
    fun `a heading only text collapses to empty`() {
        assertEquals("", cleanSpeciesText("# Bara en rubrik"))
    }

    @Test
    fun `a normal sentence with a later hash is left unchanged`() {
        val text = "Vi har sett upp till 5 # laddade tips om fågelskådning."
        assertEquals(text, cleanSpeciesText(text))
    }

    @Test
    fun `a lone star used as multiplication is left unchanged`() {
        assertEquals("5 * 3 = 15", cleanSpeciesText("5 * 3 = 15"))
    }

    @Test
    fun `handles windows line endings around the heading`() {
        assertEquals("Text", cleanSpeciesText("# X\r\n\r\nText"))
    }
}
