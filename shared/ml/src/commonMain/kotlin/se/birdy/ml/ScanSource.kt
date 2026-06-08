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
        val origin: ImageOrigin = ImageOrigin.LiveScan,
        val exifLatitude: Double? = null,
        val exifLongitude: Double? = null,
    ) : ScanSource

    data class Audio(
        override val frameJpegPath: String,
        override val classification: Classification,
        val audioWavPath: String,
    ) : ScanSource
}

/** Where a [ScanSource.Image] came from — drives how location is attached at save time. */
enum class ImageOrigin {
    /** Live camera "Look" scan — attach the current device location. */
    LiveScan,

    /** In-app take-photo — here-and-now, attach the current device location. */
    CameraCapture,

    /** Gallery upload — use the photo's EXIF GPS if present, never current location. */
    Gallery,
}
