package se.birdy.content

/**
 * Links for the species texts' credit (release 1.3.0, legal review §4, 7i-fix A). The texts are
 * AI summaries of the intro of the species' Wikipedia article in the same language, which is
 * CC BY-SA 4.0, so the profile links the article version they were written from, where it is
 * known, and the licence the texts are shared under.
 */
object WikipediaLinks {
    /** The licence of Wikipedia's text, and so of the species texts adapted from it. */
    const val TEXT_LICENSE = "CC BY-SA 4.0"

    private const val TEXT_LICENSE_DEED = "https://creativecommons.org/licenses/by-sa/4.0/"

    /**
     * The permanent link to one version of an article: `index.php?oldid=` needs no title, and it
     * keeps showing the version the text was written from after the article changes. Without a
     * usable [revision], Wikidata's redirect to the species' article in [language] instead, so the
     * credit still names the right article. No revision is stored where the pipeline's page was a
     * disambiguation or split page rather than the species' article (1.3.0 review: four shown
     * texts, see SpeciesContentCorrectionsTest); the credit's wording names the article, never a
     * version, so it stays true for this link.
     */
    fun articleUrl(
        language: Locale,
        revision: String?,
        speciesId: SpeciesId,
    ): String =
        if (revision != null && revision.isNotEmpty() && revision.all { it in '0'..'9' }) {
            "https://${language.code}.wikipedia.org/w/index.php?oldid=$revision"
        } else {
            "https://www.wikidata.org/wiki/Special:GoToLinkedPage/${language.code}wiki/${speciesId.raw}"
        }

    /** The CC BY-SA 4.0 deed in the app's language (Creative Commons has a Swedish one). */
    fun textLicenseUrl(appLanguage: Locale): String =
        when (appLanguage) {
            Locale.SV -> "${TEXT_LICENSE_DEED}deed.sv"
            Locale.EN -> TEXT_LICENSE_DEED
        }
}
