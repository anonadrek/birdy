package se.birdy.ml

/**
 * In-memory [BirdAudioClassifier] used in two roles:
 *
 * 1. **DEMO fallback** — returned by [AudioClassifierFactory] when the real BirdNET-Lite
 *    TFLite runner fails to initialise on a device (mirrors how [FakeBirdClassifier] acts
 *    as the image-pipeline DEMO fallback).
 * 2. **Test double** — injected in unit tests to exercise [AudioClassifierFactory],
 *    [AudioSessionFailureGuard], and downstream consumers without a real TFLite model.
 *
 * The [cycle] parameter lets callers supply a deterministic sequence of results; the
 * classifier loops over it indefinitely. [throwOnNext] lets tests inject failures.
 */
class FakeAudioClassifier(
    private val cycle: List<List<ClassificationResult>> =
        listOf(
            listOf(ClassificationResult("Q25334", 0.92f), ClassificationResult("Q25337", 0.04f)),
        ),
    override val info: AudioModelInfo =
        AudioModelInfo(
            modelVersion = "fake_audio_v1",
            inputShape = listOf(1, 144_000),
            outputShape = listOf(1, 6_362),
            coveragePct = 100.0,
        ),
) : BirdAudioClassifier {
    private var call = 0
    var isClosed = false
        private set
    var throwOnNext: Throwable? = null
    var calls = 0
        private set

    override suspend fun classify(input: AudioInput): AudioClassification {
        check(!isClosed) { "FakeAudioClassifier closed" }
        calls++
        throwOnNext?.let {
            throwOnNext = null
            throw it
        }
        val results = cycle[call % cycle.size]
        call++
        return AudioClassification(results = results, inferenceMs = 5L, modelVersion = info.modelVersion)
    }

    override fun close() {
        isClosed = true
    }
}
