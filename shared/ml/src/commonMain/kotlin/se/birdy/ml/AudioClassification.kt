package se.birdy.ml

data class AudioClassification(
    val top: List<ClassificationResult>,
    val inferenceMs: Long,
    val modelVersion: String,
)
