package se.birdy.ml

/**
 * Plan 1 stub. Plan 4 replaces this with a real TFLite-backed classifier.
 * Returns an empty result for now so screens that wire it up can be tested.
 */
actual class BirdClassifier {
    actual suspend fun classify(image: ImageInput): Classification = Classification(emptyList())

    actual fun close() {}
}
