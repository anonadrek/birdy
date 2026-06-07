package se.birdy.ml

import kotlinx.serialization.Serializable

/**
 * Wire-format DTO for serializing [ScanSource] through navigation arguments.
 * Plan 6b2 T5 introduces this; T6 refactors the MatchResult route to consume it.
 */
@Serializable
data class ScanSourceSerialization(
    val type: String, // "image" | "audio"
    val frameJpegPath: String,
    val classification: ClassificationSerialization,
    val audioWavPath: String? = null,
    val live: Boolean = true,
)

@Serializable
data class ClassificationSerialization(
    val results: List<ClassificationResultSerialization>,
    val frameTimestampMillis: Long = 0L,
)

@Serializable
data class ClassificationResultSerialization(
    val speciesId: String,
    val confidence: Float,
)

fun ScanSource.toSerial(): ScanSourceSerialization =
    when (this) {
        is ScanSource.Image ->
            ScanSourceSerialization(
                type = "image",
                frameJpegPath = frameJpegPath,
                classification = classification.toSerial(),
                live = live,
            )
        is ScanSource.Audio ->
            ScanSourceSerialization(
                type = "audio",
                frameJpegPath = frameJpegPath,
                classification = classification.toSerial(),
                audioWavPath = audioWavPath,
            )
    }

fun ScanSourceSerialization.toScanSource(): ScanSource =
    when (type) {
        "image" ->
            ScanSource.Image(
                frameJpegPath = frameJpegPath,
                classification = classification.toClassification(),
                live = live,
            )
        "audio" ->
            ScanSource.Audio(
                frameJpegPath = frameJpegPath,
                classification = classification.toClassification(),
                audioWavPath = audioWavPath ?: error("Audio variant missing audioWavPath"),
            )
        else -> error("Unknown ScanSource type: $type")
    }

private fun ClassificationSerialization.toClassification(): Classification =
    Classification(
        results = results.map { ClassificationResult(it.speciesId, it.confidence) },
        frameTimestampMillis = frameTimestampMillis,
    )

private fun Classification.toSerial(): ClassificationSerialization =
    ClassificationSerialization(
        results = results.map { ClassificationResultSerialization(it.speciesId, it.confidence) },
        frameTimestampMillis = frameTimestampMillis,
    )
