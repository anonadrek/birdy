package se.birdy.ml

import birdy_bird_scanner.shared.ml.generated.resources.Res
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.ExperimentalResourceApi

class AiyLabelMapper internal constructor(
    private val table: Map<Int, String>,
    val coveragePct: Double,
    val modelVersion: String,
) {
    fun lookup(classIndex: Int): String? {
        if (classIndex == BACKGROUND_CLASS_INDEX) return null
        return table[classIndex]
    }

    companion object {
        const val BACKGROUND_CLASS_INDEX = 964

        private val json = Json { ignoreUnknownKeys = true }

        fun parse(raw: String): AiyLabelMapper {
            val dto =
                try {
                    json.decodeFromString<MappingDto>(raw)
                } catch (e: SerializationException) {
                    throw IllegalArgumentException("Failed to parse aiy_to_qid.json: ${e.message}", e)
                }
            val table = dto.mappings.mapKeys { (k, _) -> k.toInt() }
            return AiyLabelMapper(
                table = table,
                coveragePct = dto.meta.coveragePct,
                modelVersion = dto.meta.generatedForModelVersion,
            )
        }
    }

    @Serializable
    private data class MappingDto(
        @SerialName("_meta") val meta: MetaDto,
        val mappings: Map<String, String>,
    )

    @Serializable
    private data class MetaDto(
        @SerialName("generated_for_model_version") val generatedForModelVersion: String,
        @SerialName("coverage_pct") val coveragePct: Double,
        @SerialName("mapped_classes") val mappedClasses: Int,
        @SerialName("total_classes") val totalClasses: Int,
    )
}

@OptIn(ExperimentalResourceApi::class)
suspend fun loadAiyLabelMapper(): AiyLabelMapper {
    val bytes = Res.readBytes("files/ml/aiy_to_qid.json")
    return AiyLabelMapper.parse(bytes.decodeToString())
}
