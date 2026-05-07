package se.birdy.ml

/**
 * Parsed representation of model_metadata.json.
 *
 * Equality compares by reference for FloatArray fields (normalizationMean, normalizationStd).
 */
@Suppress("ArrayInDataClass")
data class BirdClassifierModelInfo(
    val modelVersion: String,
    val distribution: ModelDistribution,
    val inputWidthPx: Int,
    val inputHeightPx: Int,
    val inputChannels: Int,
    /** "uint8" or "float32" — Task 7 branches on this to decide pre-normalization strategy. */
    val inputDtype: String,
    /** Reference-only when dtype=uint8; documents float-domain training semantics. */
    val normalizationMean: FloatArray,
    val normalizationStd: FloatArray,
    val outputClasses: Int,
    /** Index of the background / non-bird class (excluded from top-K results). */
    val backgroundClassIndex: Int,
    /** "uint8" — Task 7 dequantizes via (q - outputZeroPoint) * outputScale. */
    val outputDtype: String,
    val outputScale: Float,
    val outputZeroPoint: Int,
    val tfliteFileBytes: Long,
    val tfliteSha256: String,
)

enum class ModelDistribution {
    COMPOSE_RESOURCES,
}
