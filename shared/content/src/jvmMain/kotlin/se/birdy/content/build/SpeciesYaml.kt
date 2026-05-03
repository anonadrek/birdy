package se.birdy.content.build

import kotlinx.serialization.Serializable

@Serializable
data class SpeciesYaml(
    val id: String,
    val scientific_name: String,
    val taxonomy: TaxonomyYaml,
    val names: NamesYaml,
    val abundance: String,
    val iucn_status: String,
    val season: Map<String, String>,
    val regions: List<String>,
    val description: Map<String, String?> = emptyMap(),
    val migration: Map<String, String?> = emptyMap(),
    val image_refs: List<ImageRefYaml> = emptyList(),
    val review_status: String = "auto",
    val review_notes: String = "",
    val generated_at: String = "",
    val sources: SourcesYaml = SourcesYaml(),
)

@Serializable
data class TaxonomyYaml(
    val family: String,
    val family_sv: String? = null,
    val genus: String,
    val ioc_order: String,
)

@Serializable
data class NamesYaml(
    val sv: String? = null,
    val en: String,
)

@Serializable
data class ImageRefYaml(
    val role: String,
    val path: String,
    val width: Int,
    val height: Int,
    val license: String,
    val author: String,
    val source_url: String,
    val commons_filename: String,
)

@Serializable
data class SourcesYaml(
    val wikipedia_sv_revision: String? = null,
    val wikipedia_en_revision: String? = null,
    val wikidata_revision: String? = null,
    val claude_model: String? = null,
)
