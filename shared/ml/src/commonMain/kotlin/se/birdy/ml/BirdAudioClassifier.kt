package se.birdy.ml

interface BirdAudioClassifier : AutoCloseable {
    val info: AudioModelInfo

    suspend fun classify(input: AudioInput): AudioClassification

    override fun close()
}
