package se.birdy.content.model

import se.birdy.content.Abundance
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
)

data class SpeciesTaxonomy(
    val family: String,
    val familySv: String?,
    val genus: String,
    val iocOrder: String,
)

data class SpeciesImage(
    val role: String,
    val path: String,
    val width: Int,
    val height: Int,
    val license: String,
    val author: String,
    val sourceUrl: String,
)

data class SpeciesSummary(
    val id: SpeciesId,
    val name: String,
    val scientificName: String,
    val abundance: Abundance,
    val heroImagePath: String?,
    val iocOrder: String = "",
    val family: String = "",
)
