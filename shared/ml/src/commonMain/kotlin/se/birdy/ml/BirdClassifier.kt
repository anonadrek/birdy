package se.birdy.ml

/**
 * Image bytes in a platform-neutral form: raw bytes plus dimensions.
 * The actual classifier preprocesses internally.
 */
data class ImageInput(
    val bytes: ByteArray,
    val widthPx: Int,
    val heightPx: Int,
    val rotationDegrees: Int = 0,
) {
    override fun equals(other: Any?): Boolean =
        other is ImageInput &&
            bytes.contentEquals(other.bytes) &&
            widthPx == other.widthPx &&
            heightPx == other.heightPx &&
            rotationDegrees == other.rotationDegrees

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + widthPx
        result = 31 * result + heightPx
        result = 31 * result + rotationDegrees
        return result
    }
}

data class ClassificationResult(
    val speciesId: String,
    val confidence: Float,
)

data class Classification(
    val results: List<ClassificationResult>,
) {
    fun top(): ClassificationResult? = results.maxByOrNull { it.confidence }

    fun sortedByConfidenceDescending(): List<ClassificationResult> = results.sortedByDescending { it.confidence }
}

expect class BirdClassifier {
    suspend fun classify(image: ImageInput): Classification

    fun close()
}
