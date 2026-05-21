package se.birdy.ml

enum class FrameFormat { YUV_420_888, JPEG, RGBA_8888 }

data class ImageInput(
    val bytes: ByteArray,
    val widthPx: Int,
    val heightPx: Int,
    val rotationDegrees: Int = 0,
    val format: FrameFormat = FrameFormat.JPEG,
    val timestampMillis: Long = 0L,
) {
    override fun equals(other: Any?): Boolean =
        other is ImageInput &&
            bytes.contentEquals(other.bytes) &&
            widthPx == other.widthPx &&
            heightPx == other.heightPx &&
            rotationDegrees == other.rotationDegrees &&
            format == other.format &&
            timestampMillis == other.timestampMillis

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + widthPx
        result = 31 * result + heightPx
        result = 31 * result + rotationDegrees
        result = 31 * result + format.hashCode()
        result = 31 * result + timestampMillis.hashCode()
        return result
    }
}

data class ClassificationResult(
    val speciesId: String,
    val confidence: Float,
)

data class Classification(
    val results: List<ClassificationResult>,
    val frameTimestampMillis: Long = 0L,
) {
    fun top(): ClassificationResult? = results.maxByOrNull { it.confidence }

    fun sortedByConfidenceDescending(): List<ClassificationResult> = results.sortedByDescending { it.confidence }
}

interface BirdClassifier : AutoCloseable {
    suspend fun classify(image: ImageInput): Classification

    override fun close() {}
}
