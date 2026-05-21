package se.birdy.ml

actual fun normalize(pcm: ShortArray): FloatArray {
    error("AudioPreprocessor not available on JVM target — inject FakeAudioClassifier instead")
}
