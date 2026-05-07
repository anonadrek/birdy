package se.birdy.ml

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object BirdClassifierModelInfoLoader {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseJson(raw: String): BirdClassifierModelInfo {
        val dto =
            try {
                json.decodeFromString<MetadataDto>(raw)
            } catch (e: Exception) {
                throw IllegalArgumentException("Failed to parse model metadata JSON: ${e.message}", e)
            }
        return dto.toDomain()
    }

    private fun MetadataDto.toDomain(): BirdClassifierModelInfo {
        val shape = input.shape
        // shape = [batch, height, width, channels]
        return BirdClassifierModelInfo(
            modelVersion = modelVersion,
            distribution =
                when (distribution) {
                    "compose-resources" -> ModelDistribution.COMPOSE_RESOURCES
                    else -> throw IllegalArgumentException("Unknown distribution: $distribution")
                },
            inputWidthPx = shape[2],
            inputHeightPx = shape[1],
            inputChannels = shape[3],
            inputDtype = input.dtype,
            normalizationMean = input.normalization.mean.toFloatArray(),
            normalizationStd = input.normalization.std.toFloatArray(),
            outputClasses = output.outputClasses,
            backgroundClassIndex = output.backgroundClassIndex,
            outputDtype = output.dtype,
            outputScale = output.quantization.scale,
            outputZeroPoint = output.quantization.zeroPoint,
            tfliteFileBytes = tfliteFileBytes,
            tfliteSha256 = tfliteSha256,
        )
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
