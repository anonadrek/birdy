package se.birdy.ml

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
        val top = cycle[call % cycle.size]
        call++
        return AudioClassification(top = top, inferenceMs = 5L, modelVersion = info.modelVersion)
    }

    override fun close() {
        isClosed = true
    }
}
