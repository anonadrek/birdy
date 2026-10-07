package se.birdy.content.model

import se.birdy.content.Abundance
import se.birdy.content.Locale
import se.birdy.content.PhotoLicenses
import se.birdy.content.SpeciesId

data class Species(
    val id: SpeciesId,
    val scientificName: String,
    val taxonomy: SpeciesTaxonomy,
    val name: String, // localized to requested locale (sv or en, with fallback)
    val abundance: Abundance,
    val iucnStatus: String,
    val regions: List<String>,
    val season: Map<String, String>,
    val description: String?, // localized
    val migration: String?, // localized
    val images: List<SpeciesImage>,
    val marginalia: String? = null, // localized; short Caveat-rendered note shown in Profile (Plan 7c)
    // The name Birdy used before it took BirdLife Sverige's official one ("Tidigare: Sädgås"), in
    // the requested locale only: Swedish names were renamed, English ones were not (release 1.3.0
    // Task 7m). Null for every species that kept its name.
    val formerName: String? = null,
    // The Wikipedia article versions the texts shown here were written from, one per language
    // among description, migration and marginalia as actually shown (release 1.3.0, 7i-fix A).
    // Empty when no text is shown.
    val textSources: List<SpeciesTextSource> = emptyList(),
)

/**
 * A Wikipedia article version that a species text shown in the app was written from (release
 * 1.3.0, legal review §4, 7i-fix A). The texts are AI summaries of the article's intro, so the
 * profile credits the article and shares the text under CC BY-SA 4.0. [language] is the language
 * of the text as shown: English when a Swedish user sees the English fallback. [articleUrl] is
 * the permanent link to [revision], or, when no revision is stored (the stored one was a
 * disambiguation page for eight species), the species' current article through Wikidata (see
 * [se.birdy.content.WikipediaLinks]).
 */
data class SpeciesTextSource(
    val language: Locale,
    val revision: String?,
    val articleUrl: String,
)

data class SpeciesTaxonomy(
    val family: String,
    val familySv: String?,
    val genus: String,
    val iocOrder: String,
    val group: String = "",
)

data class SpeciesImage(
    val role: String,
    val path: String,
    val width: Int,
    val height: Int,
    val license: String,
    val author: String,
    val sourceUrl: String,
    // The file's name on Wikimedia Commons ("Parus major, Omsk, Russia.jpg"), for the credit's link
    // to its file page (release 1.3.0 Task 7e-2). Empty in older test fixtures, which then fall
    // back to the name at the end of [sourceUrl].
    val commonsFileName: String = "",
) {
    /** The photo's file page on Wikimedia Commons, encoded so that every file name opens. */
    val filePageUrl: String
        get() =
            PhotoLicenses.commonsFilePageUrl(
                commonsFileName.ifBlank { sourceUrl.substringAfter(COMMONS_FILE_MARKER, missingDelimiterValue = "") },
            )

    /** The licence deed, or null for public domain (no single deed) and an unknown licence. */
    val licenseUrl: String? get() = PhotoLicenses.deedUrl(license)

    private companion object {
        const val COMMONS_FILE_MARKER = "/wiki/File:"
    }
}

data class SpeciesSummary(
    val id: SpeciesId,
    val name: String,
    val scientificName: String,
    val abundance: Abundance,
    val heroImagePath: String?,
    val iocOrder: String = "",
    val family: String = "",
    val familySv: String = "",
    val group: String = "",
    // GLOBAL IUCN red-list status ("NT"/"VU"/"EN"/"CR"/"LC"/...), not a national one — powers the
    // Archive's red-listed tag (T10b, se.birdy.app.util.isRedListed).
    val iucnStatus: String = "",
)
