package se.birdy.pdf

import se.birdy.content.Locale

/**
 * Localized strings for the Field Journal PDF export (Premium feature). These used to be
 * hard-coded Swedish constants on [JournalPdfMetrics] ("avsiktligt olokaliserade") — reasonable
 * back when the PDF was treated as a fixed "paper" artifact rather than localized app UI, but
 * wrong once release 1.3.0's English Premium page started showing the exported PDF's pages: an
 * English user got a PDF with Swedish headings.
 *
 * [forLocale] resolves the one complete set to use. [se.birdy.content.Locale] only has two
 * values (SV/EN) — `LocaleResolver` has already turned "system language" plus any user override
 * into one of those two by the time it reaches [se.birdy.app.usecase.ExportJournalUseCase] (which
 * also uses that same resolved value for species lookup and byline-name masking), so there is no
 * third "SYSTEM" branch to handle here, and [forLocale] always hands back one whole, named set —
 * never a field-by-field mix of SV and EN.
 *
 * `_Fmt` strings use the `%s` placeholder consumed by [JournalPdfMetrics.fmt] (Kotlin/Native has
 * no shared `String.format`). [JournalPdfMetrics.COLOPHON], [JournalPdfMetrics.FOOTER_FMT] and
 * [JournalPdfMetrics.ORNAMENT_GLYPH] stay on [JournalPdfMetrics], unchanged for both languages:
 * the colophon is the brand name, the footer is just a page number between dashes, and the
 * ornament is a glyph — none of the three is actually translatable text.
 */
data class JournalPdfStrings(
    val title: String,
    val byFmt: String,
    val teaserFmt: String,
    val teaserSpeciesOne: String,
    val teaserSpeciesOther: String,
    val statsEyebrow: String,
    val statsTitleFmt: String,
    val statSpecies: String,
    val statTotal: String,
    val tops: String,
    val speciesEyebrow: String,
    val speciesEyebrowPagedFmt: String,
    val speciesTitle: String,
    val findOne: String,
    val findOther: String,
    val firstFmt: String,
    val badgesEyebrow: String,
    val badgesTitle: String,
    val generatedFmt: String,
) {
    /**
     * "N fynd" / "N find(s)": the count on the species page and the second half of the title
     * page's teaser. English inflects ("1 find", "7 finds", like the app's own stats plurals);
     * Swedish "fynd" doesn't, so [findOne] and [findOther] are the same string there.
     */
    fun findCount(n: Int): String = "$n ${if (n == 1) findOne else findOther}"

    /**
     * Title page's "N species seen • M finds" line. Swedish inflects the species word for
     * singular/plural ("1 art sedd" vs "3 arter sedda", release 1.3.0 Task 7g); English "species"
     * doesn't inflect, so [teaserSpeciesOne] and [teaserSpeciesOther] are deliberately the same
     * string there — see `english_teaser_species_word_is_identical_for_singular_and_plural` in
     * JournalPdfStringsTest. The finds half is [findCount].
     */
    fun teaser(
        speciesSeen: Int,
        finds: Int,
    ): String {
        val species = if (speciesSeen == 1) teaserSpeciesOne else teaserSpeciesOther
        return JournalPdfMetrics.fmt(teaserFmt, "$speciesSeen $species", findCount(finds))
    }

    companion object {
        /** Resolves the complete string set for [locale]. Exhaustive: [Locale] has only SV/EN. */
        fun forLocale(locale: Locale): JournalPdfStrings =
            when (locale) {
                Locale.SV -> SV
                Locale.EN -> EN
            }

        val SV =
            JournalPdfStrings(
                title = "Fältdagbok",
                byFmt = "av %s",
                teaserFmt = "%s • %s",
                teaserSpeciesOne = "art sedd",
                teaserSpeciesOther = "arter sedda",
                statsEyebrow = "Säsongens räkning",
                statsTitleFmt = "%s i siffror",
                statSpecies = "Arter i år",
                statTotal = "Totala fynd",
                tops = "Topparter",
                speciesEyebrow = "Arter i fält",
                speciesEyebrowPagedFmt = "Arter i fält (%s/%s)",
                speciesTitle = "Det jag sett",
                findOne = "fynd",
                findOther = "fynd",
                firstFmt = "Först: %s",
                badgesEyebrow = "Märken jag tjänat",
                badgesTitle = "Stämplar i marginalen",
                generatedFmt = "Genererad %s",
            )

        val EN =
            JournalPdfStrings(
                title = "Field journal",
                byFmt = "by %s",
                teaserFmt = "%s • %s",
                teaserSpeciesOne = "species seen",
                teaserSpeciesOther = "species seen",
                statsEyebrow = "The season's count",
                statsTitleFmt = "%s in numbers",
                statSpecies = "Species this year",
                statTotal = "Total finds",
                tops = "Top species",
                speciesEyebrow = "Species in the field",
                speciesEyebrowPagedFmt = "Species in the field (%s/%s)",
                speciesTitle = "What I have seen",
                findOne = "find",
                findOther = "finds",
                firstFmt = "First: %s",
                badgesEyebrow = "Badges I have earned",
                badgesTitle = "Stamps in the margin",
                generatedFmt = "Generated %s",
            )
    }
}
