package se.birdy.ml

/**
 * Unifies image-based and audio-based classification results for the Match-flow.
 * Both variants carry a [frameJpegPath] so the Match screen can display a thumbnail.
 */
sealed interface ScanSource {
    val frameJpegPath: String
    val classification: Classification

    data class Image(
        override val frameJpegPath: String,
        override val classification: Classification,
    ) : ScanSource

    data class Audio(
        override val frameJpegPath: String,
        override val classification: Classification,
        val audioWavPath: String?,
    ) : ScanSource
}
