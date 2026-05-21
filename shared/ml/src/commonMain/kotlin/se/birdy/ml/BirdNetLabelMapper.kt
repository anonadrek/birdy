package se.birdy.ml

import birdy_bird_scanner.shared.ml.generated.resources.Res
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.ExperimentalResourceApi

class BirdNetLabelMapper internal constructor(
    private val indexToQid: Map<Int, String>,
    val modelVersion: String,
    val coveragePct: Double,
) {
    fun lookup(classIndex: Int): String? = indexToQid[classIndex]

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(raw: String): BirdNetLabelMapper {
            val dto =
                try {
                    json.decodeFromString<MappingDto>(raw)
                } catch (e: SerializationException) {
                    throw IllegalArgumentException("Failed to parse birdnet_lite_to_qid.json: ${e.message}", e)
                }
            val table = dto.mapping.mapKeys { (k, _) -> k.toInt() }
            return BirdNetLabelMapper(
                indexToQid = table,
                modelVersion = dto.meta.generatedForModelVersion,
                coveragePct = dto.meta.coveragePct,
            )
        }
    }

    @Serializable
    private data class MappingDto(
        @SerialName("_meta") val meta: MetaDto,
        val mapping: Map<String, String>,
    )

    @Serializable
    private data class MetaDto(
        @SerialName("generated_for_model_version") val generatedForModelVersion: String,
        @SerialName("coverage_pct") val coveragePct: Double,
        @SerialName("total_birdnet_classes") val totalBirdnetClasses: Int,
        @SerialName("mapped_to_qid") val mappedToQid: Int,
        @SerialName("total_species_in_list") val totalSpeciesInList: Int,
    )
}

@OptIn(ExperimentalResourceApi::class)
suspend fun loadBirdNetLabelMapper(): BirdNetLabelMapper {
    val bytes = Res.readBytes("files/ml/birdnet_lite_to_qid.json")
    return BirdNetLabelMapper.parse(bytes.decodeToString())
}
