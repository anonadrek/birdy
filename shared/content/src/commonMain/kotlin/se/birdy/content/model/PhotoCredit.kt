package se.birdy.content.model

import se.birdy.content.PhotoLicenses
import se.birdy.content.SpeciesId

/**
 * One species photo's credit for the "Bildkällor" list under About (release 1.3.0, Task 7e-2):
 * who took it, under which licence, and where the original is. [speciesName] is in the requested
 * locale, with the English name and then the scientific one as fallback. [role] is "hero" or
 * "secondary"; [path] ("Q25485/secondary-1.webp") is unique per photo.
 */
data class PhotoCredit(
    val speciesId: SpeciesId,
    val speciesName: String,
    val scientificName: String,
    val role: String,
    val path: String,
    val license: String,
    val author: String,
    val commonsFileName: String,
) {
    /** The licence deed, or null for public domain (no single deed). */
    val licenseUrl: String? get() = PhotoLicenses.deedUrl(license)

    /** The photo's file page on Wikimedia Commons. */
    val filePageUrl: String get() = PhotoLicenses.commonsFilePageUrl(commonsFileName)
}
