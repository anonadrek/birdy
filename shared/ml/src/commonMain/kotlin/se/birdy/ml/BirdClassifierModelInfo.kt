package se.birdy.ml

data class BirdClassifierModelInfo(
    val modelVersion: String,
    val distribution: ModelDistribution,
    val inputWidthPx: Int,
    val inputHeightPx: Int,
    val inputChannels: Int,
    val inputDtype: TensorDtype,
    val normalizationMean: List<Float>,
    val normalizationStd: List<Float>,
    val outputClasses: Int,
    val backgroundClassIndex: Int,
    val outputDtype: TensorDtype,
    val outputScale: Float,
    val outputZeroPoint: Int,
    val tfliteFileBytes: Long,
    val tfliteSha256: String,
)

enum class ModelDistribution {
    COMPOSE_RESOURCES,
}

enum class TensorDtype {
    UINT8,
    FLOAT32,
}
