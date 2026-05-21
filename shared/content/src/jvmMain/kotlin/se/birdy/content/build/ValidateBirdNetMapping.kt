package se.birdy.content.build

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

private val MAPPING_BLOCK_REGEX = Regex(""""mapping"\s*:\s*\{([^}]*)""")
private val MAPPING_INDEX_REGEX = Regex(""""(\d+)"\s*:""")

@Serializable
internal data class BirdNetMappingFileDto(
    @SerialName("_meta") val meta: BirdNetMappingMetaDto,
    val mapping: Map<String, String>,
)

@Serializable
internal data class BirdNetMappingMetaDto(
    @SerialName("generated_for_model_version") val generatedForModelVersion: String,
    @SerialName("coverage_pct") val coveragePct: Double,
    @SerialName("total_birdnet_classes") val totalBirdnetClasses: Int,
    @SerialName("mapped_to_qid") val mappedToQid: Int,
    @SerialName("total_species_in_list") val totalSpeciesInList: Int,
)

@Serializable
internal data class SpeciesEntry(
    @SerialName("wikidata_id") val wikidataId: String,
    @SerialName("scientific_name") val scientificName: String,
)

/**
 * Validates the BirdNET-Lite mapping JSON (`birdnet_lite_to_qid.json`) against the species list
 * (`species_list.yaml`) at build time. Crashes the build if the mapping is malformed, has
 * duplicate class indices in the `mapping` object, `_meta.coverage_pct` falls below
 * `COVERAGE_FAIL_PCT`, or references Q-IDs not present in the species list.
 */
object ValidateBirdNetMapping {
    private const val COVERAGE_FAIL_PCT = 70.0

    private val lenientJson = Json { ignoreUnknownKeys = true }

    // strictMode = false: species_list.yaml has extra fields beyond wikidata_id/scientific_name
    // (family, ioc_order, ...) — we only read what we need.
    private val lenientYaml = Yaml(configuration = YamlConfiguration(strictMode = false))

    /** Validates [mappingJson] against [speciesYaml]; throws on any error. */
    fun validate(
        mappingJson: String,
        speciesYaml: String,
    ) {
        val mapping =
            try {
                lenientJson.decodeFromString<BirdNetMappingFileDto>(mappingJson)
            } catch (e: SerializationException) {
                throw IllegalArgumentException("Malformed birdnet_lite_to_qid.json: ${e.message}", e)
            }

        val speciesList =
            try {
                lenientYaml.decodeFromString(ListSerializer(SpeciesEntry.serializer()), speciesYaml)
            } catch (e: SerializationException) {
                throw IllegalArgumentException("Malformed species_list.yaml: ${e.message}", e)
            }

        val errors = mutableListOf<String>()

        // Coverage check
        val coverage = mapping.meta.coveragePct
        if (coverage < COVERAGE_FAIL_PCT) {
            errors += "coverage_pct $coverage% < minimum $COVERAGE_FAIL_PCT%"
        }

        // Duplicate class-index detection via raw-text scan
        // kotlinx.serialization silently overwrites duplicate keys so we must scan the raw JSON
        val mappingBlockMatch = MAPPING_BLOCK_REGEX.find(mappingJson)
        if (mappingBlockMatch != null) {
            val mappingBlock = mappingBlockMatch.groupValues[1]
            val indexMatches = MAPPING_INDEX_REGEX.findAll(mappingBlock).toList()
            if (indexMatches.size != mapping.mapping.size) {
                errors += "duplicate class index in birdnet_lite_to_qid.json mapping" +
                    " (raw count ${indexMatches.size} vs parsed count ${mapping.mapping.size})"
            }
        }

        // Q-ID validity check against species list
        val validQids = speciesList.map { it.wikidataId }.toSet()
        val invalidQids = mapping.mapping.values.filterNot { it in validQids }
        if (invalidQids.isNotEmpty()) {
            errors += "mapping references ${invalidQids.size} Q-IDs not in species_list: " +
                invalidQids.take(5).toString()
        }

        if (errors.isNotEmpty()) {
            error("validateBirdNetMapping failed:\n  - " + errors.joinToString("\n  - "))
        }
    }

    /** Reads both files from disk and delegates to [validate]. */
    fun runFromFiles(
        mappingFile: File,
        speciesFile: File,
    ) {
        validate(mappingFile.readText(), speciesFile.readText())
    }
}
