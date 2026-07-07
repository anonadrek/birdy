package se.birdy.ml

/**
 * iOS actual. Real CoreGraphics-based preprocessing lands in plan i2 together with
 * the AVFoundation camera; until then nothing on iOS produces ImageInput frames
 * (the scan flow is stubbed), so this must never be reached.
 */
actual class ImagePreprocessor actual constructor() {
    actual fun preprocess(
        input: ImageInput,
        outHeight: Int,
        outWidth: Int,
        normalizationMean: FloatArray,
        normalizationStd: FloatArray,
    ): FloatArray = throw UnsupportedOperationException("ImagePreprocessor lands on iOS in plan i2")
}
