package se.birdy.ml

import birdy_bird_scanner.shared.ml.generated.resources.Res
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.ExperimentalResourceApi

/**
 * Loads [BirdClassifierModelInfo] from the bundled `files/ml/model_metadata.json` in the
 * shared/ml compose-resources bundle. Mirrors [loadAiyLabelMapper] in the same pattern.
 */
@OptIn(ExperimentalResourceApi::class)
suspend fun loadModelMetadata(): BirdClassifierModelInfo {
    val bytes = Res.readBytes("files/ml/model_metadata.json")
    return BirdClassifierModelInfoLoader.parseJson(bytes.decodeToString())
}

object BirdClassifierModelInfoLoader {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseJson(raw: String): BirdClassifierModelInfo {
        val dto =
            try {
                json.decodeFromString<MetadataDto>(raw)
            } catch (e: SerializationException) {
                throw IllegalArgumentException("Failed to parse model metadata JSON: ${e.message}", e)
            }
        return dto.toDomain()
    }

    private fun MetadataDto.toDomain(): BirdClassifierModelInfo {
        require(input.shape.size == 4) {
            "input.shape must be [batch, height, width, channels]; got ${input.shape}"
        }
        require(output.shape.size == 2) {
            "output.shape must be [batch, classes]; got ${output.shape}"
        }
        return BirdClassifierModelInfo(
            modelVersion = modelVersion,
            distribution =
                when (distribution) {
                    "compose-resources" -> ModelDistribution.COMPOSE_RESOURCES
                    else -> throw IllegalArgumentException("Unknown distribution: $distribution")
                },
            inputWidthPx = input.shape[2],
            inputHeightPx = input.shape[1],
            inputChannels = input.shape[3],
            inputDtype = parseDtype(input.dtype),
            normalizationMean = input.normalization.mean,
            normalizationStd = input.normalization.std,
            outputClasses = output.outputClasses,
            backgroundClassIndex = output.backgroundClassIndex,
            outputDtype = parseDtype(output.dtype),
            outputScale = output.quantization.scale,
            outputZeroPoint = output.quantization.zeroPoint,
            tfliteFileBytes = tfliteFileBytes,
            tfliteSha256 = tfliteSha256,
        )
    }

    private fun parseDtype(raw: String): TensorDtype =
        when (raw) {
            "uint8" -> TensorDtype.UINT8
            "float32" -> TensorDtype.FLOAT32
            else -> throw IllegalArgumentException("Unsupported dtype: $raw")
        }

    @Serializable
    private data class MetadataDto(
        val modelVersion: String,
        val distribution: String = "compose-resources",
        val input: InputDto,
        val output: OutputDto,
        val tfliteFileBytes: Long = 0L,
        val tfliteSha256: String = "",
    )

    @Serializable
    private data class InputDto(
        val shape: List<Int>,
        val dtype: String,
        val normalization: NormDto,
    )

    @Serializable
    private data class NormDto(
        val mean: List<Float>,
        val std: List<Float>,
    )

    @Serializable
    private data class OutputDto(
        val shape: List<Int>,
        val dtype: String,
        val labelFormat: String,
        val outputClasses: Int,
        val backgroundClassIndex: Int,
        val quantization: QuantizationDto,
    )

    @Serializable
    private data class QuantizationDto(
        val scale: Float,
        val zeroPoint: Int,
    )
}
