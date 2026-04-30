package se.birdy.ml

actual class BirdClassifier {
    actual suspend fun classify(image: ImageInput): Classification = Classification(emptyList())

    actual fun close() {}
}
