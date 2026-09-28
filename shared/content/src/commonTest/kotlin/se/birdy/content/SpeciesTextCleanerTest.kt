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

    // --- T11e: bold prose is not a heading ---

    @Test
    fun `a first line with several bold words is prose not a heading`() {
        assertEquals(
            "Talgoxen är en vanlig fågel i trädgårdar",
            cleanSpeciesText("**Talgoxen** är en **vanlig** fågel i **trädgårdar**"),
        )
    }

    @Test
    fun `a fully bold first sentence is prose not a heading`() {
        assertEquals(
            "Talgoxen är en vanlig fågel.\n\nDen häckar i holkar.",
            cleanSpeciesText("**Talgoxen är en vanlig fågel.**\n\nDen häckar i holkar."),
        )
    }

    @Test
    fun `a fully bold first line ending in a question mark is kept`() {
        assertEquals("Var finns den?", cleanSpeciesText("**Var finns den?**"))
    }

    // --- T11e: no-data sentinel and meta-commentary texts collapse to blank ---

    @Test
    fun `the english no data sentinel alone collapses to empty`() {
        assertEquals("", cleanSpeciesText("Migration data unavailable for this species."))
    }

    @Test
    fun `the swedish no data sentinel alone collapses to empty`() {
        assertEquals("", cleanSpeciesText("Migrationsdata saknas för denna art."))
    }

    @Test
    fun `the sentinel followed by meta commentary collapses to empty`() {
        val raw =
            "Migration data unavailable for this species.\n\n" +
                "The provided source text contains no information about migration."
        assertEquals("", cleanSpeciesText(raw))
    }

    @Test
    fun `the sentinel under a heading collapses to empty`() {
        val raw = "# Pilgrimsfalk – Förekomst i Nordeuropa\n\nMigrationsdata saknas för denna art."
        assertEquals("", cleanSpeciesText(raw))
    }

    @Test
    fun `a quoted and language prefixed sentinel collapses to empty`() {
        val raw =
            "sv: \"Migrationsdata saknas för denna art.\"\n\n" +
                "en: \"Migration data unavailable for this species.\"\n\n---\n\n" +
                "**Förklaring:** Källtexten innehåller ingen information om flyttning."
        assertEquals("", cleanSpeciesText(raw))
    }

    @Test
    fun `an unquoted language prefixed sentinel collapses to empty`() {
        val raw = "sv: Migrationsdata saknas för denna art.\n\nen: Migration data unavailable for this species."
        assertEquals("", cleanSpeciesText(raw))
    }

    @Test
    fun `a bold wrapped sentinel collapses to empty`() {
        val raw = "**Migrationsdata saknas för denna art.**\n\nKälltexten nämner inget om flyttning."
        assertEquals("", cleanSpeciesText(raw))
    }

    @Test
    fun `a text opening with english source meta commentary collapses to empty`() {
        assertEquals("", cleanSpeciesText("The source text provides no information about migration."))
        assertEquals("", cleanSpeciesText("the source text provides no information about migration."))
        assertEquals("", cleanSpeciesText("The provided source text contains only taxonomy."))
        assertEquals("", cleanSpeciesText("The Wikipedia source text provided contains no information."))
    }

    @Test
    fun `a text opening with swedish source meta commentary collapses to empty`() {
        assertEquals("", cleanSpeciesText("Källtexten innehåller ingen information om flyttning."))
        assertEquals("", cleanSpeciesText("källtexten innehåller ingen information om flyttning."))
    }

    @Test
    fun `meta commentary under a heading collapses to empty`() {
        val raw = "# Migration Data Unavailable for This Species\n\nThe provided source text contains only taxonomy."
        assertEquals("", cleanSpeciesText(raw))
    }

    @Test
    fun `a near miss of the sentinel text elsewhere in the paragraph is kept`() {
        val sentinelLater =
            "The Iago sparrow does not occur in Scandinavia.\n\n" +
                "Migration data unavailable for this species in a Northern European context."
        assertEquals(sentinelLater, cleanSpeciesText(sentinelLater))
    }

    @Test
    fun `a sentence that only resembles the meta commentary opening is kept`() {
        val raw = "The sourcebook describes the species well."
        assertEquals(raw, cleanSpeciesText(raw))
    }

    // --- T11f: sentinel or labelled meta-commentary as the FINAL paragraph also collapses ---

    @Test
    fun `an exact sentinel as the final paragraph is dropped`() {
        val raw = "Stenfalken häckar i fjällen.\n\nMigrationsdata saknas för denna art."
        assertEquals("Stenfalken häckar i fjällen.", cleanSpeciesText(raw))
    }

    @Test
    fun `swedish and english sentinels as trailing paragraphs are both dropped`() {
        val raw =
            "Stenfalken häckar i fjällen.\n\n" +
                "Migrationsdata saknas för denna art.\n\n" +
                "Migration data unavailable for this species."
        assertEquals("Stenfalken häckar i fjällen.", cleanSpeciesText(raw))
    }

    @Test
    fun `a labelled meta commentary paragraph at the end is dropped`() {
        val raw =
            "Stenfalken häckar i fjällen.\n\n" +
                "**Notering:** Källtexten innehåller begränsad information om flyttning."
        assertEquals("Stenfalken häckar i fjällen.", cleanSpeciesText(raw))
    }

    @Test
    fun `a whole paragraph wrapped in single star emphasis with a label is dropped`() {
        val raw =
            "Stenfalken häckar i fjällen.\n\n" +
                "*Notering: Källtexten innehåller begränsad migrationsinformation. " +
                "Denna text baseras på allmän kunskapsgrund om arten.*"
        assertEquals("Stenfalken häckar i fjällen.", cleanSpeciesText(raw))
    }

    @Test
    fun `unlabelled trailing meta commentary about the source text is also dropped`() {
        val raw = "Stenfalken häckar i fjällen.\n\nKälltexten nämner också Öland."
        assertEquals("Stenfalken häckar i fjällen.", cleanSpeciesText(raw))
    }

    @Test
    fun `real prose about missing migration data in the ordinary sense is kept`() {
        val raw =
            "Stenfalken häckar i fjällen.\n\n" +
                "Migrationsdata saknas för denna art i praktisk bemärkelse, då den är en stannfågel."
        assertEquals(raw, cleanSpeciesText(raw))
    }

    @Test
    fun `a trailing sentinel survives windows line endings`() {
        val raw = "Stenfalken häckar i fjällen.\r\n\r\nMigrationsdata saknas för denna art."
        assertEquals("Stenfalken häckar i fjällen.", cleanSpeciesText(raw))
    }

    // --- T11f / Minor C3: the sentinel written as an ATX heading ---

    @Test
    fun `a no-data sentinel written as an atx heading collapses to empty`() {
        val raw = "# Migration Data Unavailable for This Species"
        assertEquals("", cleanSpeciesText(raw))
    }

    @Test
    fun `a no-data heading followed by other meta text still collapses to empty`() {
        val raw =
            "# Migration Data Unavailable for This Species\n\n" +
                "Detailed migration data cannot be provided due to source limitations."
        assertEquals("", cleanSpeciesText(raw))
    }

    // --- T11f / Minor T1: heading edge cases ---

    @Test
    fun `a hash immediately followed by a word is not a heading`() {
        val raw = "#Talgoxe är en vanlig fågel."
        assertEquals(raw, cleanSpeciesText(raw))
    }

    @Test
    fun `seven hashes is not a valid atx heading`() {
        val raw = "####### Rubrik\n\nTexten."
        assertEquals(raw, cleanSpeciesText(raw))
    }

    @Test
    fun `a colon terminated bold label as the only first line is treated as a heading`() {
        val raw = "**Rubrik:**\n\nTexten som följer."
        assertEquals("Texten som följer.", cleanSpeciesText(raw))
    }

    @Test
    fun `a trailing horizontal rule at the very end of the text is dropped`() {
        val raw = "Texten.\n\n---"
        assertEquals("Texten.", cleanSpeciesText(raw))
    }

    // --- T11e: horizontal rules ---

    @Test
    fun `a horizontal rule line between paragraphs is dropped`() {
        assertEquals(
            "Första stycket.\n\nAndra stycket.",
            cleanSpeciesText("Första stycket.\n\n---\n\nAndra stycket."),
        )
    }

    @Test
    fun `spaced star and underscore rules are dropped`() {
        assertEquals("A\n\nB\n\nC", cleanSpeciesText("A\n\n* * *\n\nB\n\n_____\n\nC"))
    }

    @Test
    fun `two dashes or dashes inside prose are not a rule`() {
        val raw = "A\n--\nB --- C"
        assertEquals(raw, cleanSpeciesText(raw))
    }

    // --- T11e: emphasis edge cases ---

    @Test
    fun `a star span followed by punctuation is unwrapped`() {
        assertEquals("Släktet Ardea.", cleanSpeciesText("Släktet *Ardea*."))
    }

    @Test
    fun `several star spans on one line are unwrapped`() {
        assertEquals("a, b", cleanSpeciesText("*a*, *b*"))
    }

    @Test
    fun `stars inside a word are left alone`() {
        assertEquals("a*b*c", cleanSpeciesText("a*b*c"))
    }

    @Test
    fun `a bold label later in the text keeps its words`() {
        val raw = "Första raden.\n\n**Förklaring:** Stenfalken jagar småfåglar."
        assertEquals(
            "Första raden.\n\nFörklaring: Stenfalken jagar småfåglar.",
            cleanSpeciesText(raw),
        )
    }
}
