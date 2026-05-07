package se.birdy.ml

actual class ImagePreprocessor actual constructor() {
    actual fun preprocess(
        input: ImageInput,
        outHeight: Int,
        outWidth: Int,
        normalizationMean: FloatArray,
        normalizationStd: FloatArray,
    ): FloatArray =
        throw UnsupportedOperationException(
            "ImagePreprocessor JVM actual is a stub. " +
                "Use a test-double in commonTest, or wait for Task 8 micro-model JVM impl.",
        )
}
