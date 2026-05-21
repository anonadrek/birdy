package se.birdy.ml

data class AudioClassification(
    val results: List<ClassificationResult>,
    val inferenceMs: Long,
    val modelVersion: String,
)
