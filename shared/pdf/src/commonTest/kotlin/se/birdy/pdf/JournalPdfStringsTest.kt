package se.birdy.pdf

import se.birdy.content.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Field Journal PDF's text used to be hard-coded Swedish on [JournalPdfMetrics] ("avsiktligt
 * olokaliserade") — correct when the PDF was just a fixed "paper" artifact, wrong once the 1.3.0
 * website started showing the English Premium page's exported pages: an English user's PDF
 * printed Swedish headings. [JournalPdfStrings] fixes that by resolving a complete, per-locale
 * set from [se.birdy.content.Locale] (which only has two values — `LocaleResolver` has already
 * turned "system language" into one of them by the time it reaches
 * [se.birdy.app.usecase.ExportJournalUseCase], so there's no third branch and no risk of a
 * SV/EN mix: [JournalPdfStrings.forLocale] always returns one complete, named set).
 */
class JournalPdfStringsTest {
    @Test
    fun forLocale_sv_resolves_to_the_swedish_set() {
        assertEquals(JournalPdfStrings.SV, JournalPdfStrings.forLocale(Locale.SV))
    }

    @Test
    fun forLocale_en_resolves_to_the_english_set() {
        assertEquals(JournalPdfStrings.EN, JournalPdfStrings.forLocale(Locale.EN))
    }

    @Test
    fun every_swedish_string_is_non_blank() {
        assertTrue(allValues(JournalPdfStrings.SV).all { it.isNotBlank() })
    }

    @Test
    fun every_english_string_is_non_blank() {
        assertTrue(allValues(JournalPdfStrings.EN).all { it.isNotBlank() })
    }

    // Swedish equals today's values (JournalPdfMetrics' former hard-coded constants), byte for
    // byte — these must never drift just because the English set now exists alongside them.
    @Test
    fun swedish_strings_match_todays_values() {
        val sv = JournalPdfStrings.SV
        assertEquals("Fältdagbok", sv.title)
        assertEquals("av %s", sv.byFmt)
        assertEquals("%s • %s", sv.teaserFmt)
        assertEquals("art sedd", sv.teaserSpeciesOne)
        assertEquals("arter sedda", sv.teaserSpeciesOther)
        assertEquals("Säsongens räkning", sv.statsEyebrow)
        assertEquals("%s i siffror", sv.statsTitleFmt)
        assertEquals("Arter i år", sv.statSpecies)
        assertEquals("Totala fynd", sv.statTotal)
        assertEquals("Topparter", sv.tops)
        assertEquals("Arter i fält", sv.speciesEyebrow)
        assertEquals("Arter i fält (%s/%s)", sv.speciesEyebrowPagedFmt)
        assertEquals("Det jag sett", sv.speciesTitle)
        assertEquals("fynd", sv.findOne)
        assertEquals("fynd", sv.findOther)
        assertEquals("Först: %s", sv.firstFmt)
        assertEquals("Märken jag tjänat", sv.badgesEyebrow)
        assertEquals("Stämplar i marginalen", sv.badgesTitle)
        assertEquals("Genererad %s", sv.generatedFmt)
    }

    // English is natural English, not a word-for-word mirror of the Swedish set.
    @Test
    fun english_strings_are_natural_english() {
        val en = JournalPdfStrings.EN
        assertEquals("Field journal", en.title)
        assertEquals("by %s", en.byFmt)
        assertEquals("%s • %s", en.teaserFmt)
        assertEquals("The season's count", en.statsEyebrow)
        assertEquals("%s in numbers", en.statsTitleFmt)
        assertEquals("Species this year", en.statSpecies)
        assertEquals("Total finds", en.statTotal)
        assertEquals("Top species", en.tops)
        assertEquals("Species in the field", en.speciesEyebrow)
        assertEquals("Species in the field (%s/%s)", en.speciesEyebrowPagedFmt)
        assertEquals("What I have seen", en.speciesTitle)
        assertEquals("find", en.findOne)
        assertEquals("finds", en.findOther)
        assertEquals("First: %s", en.firstFmt)
        assertEquals("Badges I have earned", en.badgesEyebrow)
        assertEquals("Stamps in the margin", en.badgesTitle)
        assertEquals("Generated %s", en.generatedFmt)
    }

    // Swedish genuinely inflects "art"/"arter" for the species word (Task 7g, release 1.3.0).
    @Test
    fun swedish_teaser_distinguishes_singular_and_plural_species() {
        assertEquals("1 art sedd • 1 fynd", JournalPdfStrings.SV.teaser(speciesSeen = 1, finds = 1))
        assertEquals("3 arter sedda • 7 fynd", JournalPdfStrings.SV.teaser(speciesSeen = 3, finds = 7))
        assertEquals("0 arter sedda • 0 fynd", JournalPdfStrings.SV.teaser(speciesSeen = 0, finds = 0))
    }

    // English "species" doesn't inflect: the singular and plural teaser word are the SAME string.
    // Explicit about that (not just "happens to look the same") so a future edit can't quietly
    // split them without this test calling it out.
    @Test
    fun english_teaser_species_word_is_identical_for_singular_and_plural() {
        assertEquals(JournalPdfStrings.EN.teaserSpeciesOne, JournalPdfStrings.EN.teaserSpeciesOther)
        assertEquals("species seen", JournalPdfStrings.EN.teaserSpeciesOne)
    }

    // Swedish "fynd" doesn't inflect, so the rendered text is exactly what the PDF printed before
    // the per-locale sets existed ("%s fynd" on the species page).
    @Test
    fun swedish_find_count_is_unchanged() {
        assertEquals("1 fynd", JournalPdfStrings.SV.findCount(1))
        assertEquals("7 fynd", JournalPdfStrings.SV.findCount(7))
        assertEquals("0 fynd", JournalPdfStrings.SV.findCount(0))
    }

    // English "find" does inflect, like the app's own stats plurals ("1 find", "N finds"); the
    // first version printed "1 finds" (review 2026-10-08).
    @Test
    fun english_find_count_inflects() {
        assertEquals("1 find", JournalPdfStrings.EN.findCount(1))
        assertEquals("7 finds", JournalPdfStrings.EN.findCount(7))
        assertEquals("0 finds", JournalPdfStrings.EN.findCount(0))
    }

    @Test
    fun english_teaser_reads_naturally_for_one_and_many() {
        assertEquals("1 species seen • 1 find", JournalPdfStrings.EN.teaser(speciesSeen = 1, finds = 1))
        assertEquals("3 species seen • 7 finds", JournalPdfStrings.EN.teaser(speciesSeen = 3, finds = 7))
        assertEquals("0 species seen • 0 finds", JournalPdfStrings.EN.teaser(speciesSeen = 0, finds = 0))
    }

    private fun allValues(strings: JournalPdfStrings): List<String> =
        listOf(
            strings.title,
            strings.byFmt,
            strings.teaserFmt,
            strings.teaserSpeciesOne,
            strings.teaserSpeciesOther,
            strings.statsEyebrow,
            strings.statsTitleFmt,
            strings.statSpecies,
            strings.statTotal,
            strings.tops,
            strings.speciesEyebrow,
            strings.speciesEyebrowPagedFmt,
            strings.speciesTitle,
            strings.findOne,
            strings.findOther,
            strings.firstFmt,
            strings.badgesEyebrow,
            strings.badgesTitle,
            strings.generatedFmt,
        )
}
