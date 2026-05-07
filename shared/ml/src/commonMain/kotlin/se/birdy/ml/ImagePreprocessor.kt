package se.birdy.ml

expect class ImagePreprocessor() {
    /**
     * Returns a FloatArray of size outHeight * outWidth * 3, row-major, RGB
     * channel order, normalized via per-channel mean/std: out = (px/255 - mean) / std.
     */
    fun preprocess(
        input: ImageInput,
        outHeight: Int,
        outWidth: Int,
        normalizationMean: FloatArray,
        normalizationStd: FloatArray,
    ): FloatArray
}
