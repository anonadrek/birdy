package se.birdy.content.build

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File

private val MAPPINGS_BLOCK_REGEX = Regex(""""mappings"\s*:\s*\{([^}]*)""")
private val MAPPING_INDEX_REGEX = Regex(""""(\d+)"\s*:""")

@Serializable
internal data class MappingFileDto(
    @SerialName("_meta") val meta: MappingMetaDto,
    val mappings: Map<String, String>,
)

@Serializable
internal data class MappingMetaDto(
    @SerialName("generated_for_model_version") val generatedForModelVersion: String,
    @SerialName("coverage_pct") val coveragePct: Double,
    @SerialName("mapped_classes") val mappedClasses: Int,
    @SerialName("total_classes") val totalClasses: Int,
)

@Serializable
internal data class MetadataFileDto(
    val modelVersion: String,
)

/**
 * Validates the AIY V1 mapping JSON (`aiy_to_qid.json`) against the model
 * metadata (`model_metadata.json`) at build time. Crashes the build if the
 * mapping is malformed, version-mismatched against the metadata, has duplicate
 * class indices in the `mappings` object, or `_meta.coverage_pct` falls below
 * `COVERAGE_FAIL_PCT`. Q-IDs in the mapping that are not present in
 * `species_list.yaml` are tolerated (handled at runtime via the unresolved
 * pill — see Plan 4a).
 */
object ValidateModelMapping {
    private const val COVERAGE_FAIL_PCT = 50.0
    private const val COVERAGE_WARN_PCT = 90.0

    private val lenientJson = Json { ignoreUnknownKeys = true }

    /** Validates [mappingJson] against [metadataJson]; throws on any error. */
    fun validate(
        mappingJson: String,
        metadataJson: String,
    ) {
        val mapping =
            try {
                lenientJson.decodeFromString<MappingFileDto>(mappingJson)
            } catch (e: SerializationException) {
                throw IllegalArgumentException("Malformed aiy_to_qid.json: ${e.message}", e)
            }

        val metadata =
            try {
                lenientJson.decodeFromString<MetadataFileDto>(metadataJson)
            } catch (e: SerializationException) {
                throw IllegalArgumentException("Malformed model_metadata.json: ${e.message}", e)
            }

        val errors = mutableListOf<String>()

        // Version check
        if (mapping.meta.generatedForModelVersion != metadata.modelVersion) {
            errors +=
                "version mismatch: mapping says '${mapping.meta.generatedForModelVersion}'" +
                " but metadata says '${metadata.modelVersion}'"
        }

        // Coverage check
        val coverage = mapping.meta.coveragePct
        if (coverage < COVERAGE_FAIL_PCT) {
            errors += "coverage too low: $coverage% (minimum $COVERAGE_FAIL_PCT%)"
        } else if (coverage < COVERAGE_WARN_PCT) {
            println(
                "WARN: coverage $coverage% — kör birdy-fetcher build-mapping igen för att" +
                    " förbättra täckningen (threshold $COVERAGE_WARN_PCT%)",
            )
        }

        // Duplicate class-index detection via raw-text scan
        // kotlinx.serialization silently overwrites duplicate keys so we must scan the raw JSON
        val mappingsBlockMatch = MAPPINGS_BLOCK_REGEX.find(mappingJson)
        if (mappingsBlockMatch != null) {
            val mappingsBlock = mappingsBlockMatch.groupValues[1]
            val indexMatches = MAPPING_INDEX_REGEX.findAll(mappingsBlock).toList()
            if (indexMatches.size != mapping.mappings.size) {
                errors += "duplicate class index in aiy_to_qid.json mappings" +
                    " (raw count ${indexMatches.size} vs parsed count ${mapping.mappings.size})"
            }
        }

        if (errors.isNotEmpty()) {
            error("validateModelMapping failed:\n  - " + errors.joinToString("\n  - "))
        }
    }

    /** Reads both files from disk and delegates to [validate]. */
    fun runFromFiles(
        mappingFile: File,
        metadataFile: File,
    ) {
        validate(mappingFile.readText(), metadataFile.readText())
    }
}
