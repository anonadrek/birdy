package se.birdy.ml

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class FakeBirdClassifier(
    private val cycle: List<List<ClassificationResult>> = DEFAULT_CYCLE,
) : BirdClassifier {
    private val mutex = Mutex()
    private var index: Int = 0

    override suspend fun classify(image: ImageInput): Classification {
        val results =
            mutex.withLock {
                val current = cycle[index % cycle.size]
                index = (index + 1) % cycle.size
                current
            }
        return Classification(
            results = results,
            frameTimestampMillis = image.timestampMillis,
        )
    }

    companion object {
        val DEFAULT_CYCLE: List<List<ClassificationResult>> =
            listOf(
                listOf(
                    ClassificationResult("Q25485", 0.87f),
                    ClassificationResult("Q25404", 0.08f),
                    ClassificationResult("Q25234", 0.02f),
                ),
                listOf(
                    ClassificationResult("Q25234", 0.74f),
                    ClassificationResult("Q25402", 0.18f),
                    ClassificationResult("Q26490", 0.04f),
                ),
                listOf(
                    ClassificationResult("Q25404", 0.91f),
                    ClassificationResult("Q25485", 0.06f),
                    ClassificationResult("Q25234", 0.01f),
                ),
                listOf(
                    ClassificationResult("Q25402", 0.82f),
                    ClassificationResult("Q26490", 0.10f),
                    ClassificationResult("Q25234", 0.04f),
                ),
                listOf(
                    ClassificationResult("Q26490", 0.68f),
                    ClassificationResult("Q25485", 0.20f),
                    ClassificationResult("Q25404", 0.07f),
                ),
                listOf(
                    ClassificationResult("Q_LOW", 0.22f),
                    ClassificationResult("Q25485", 0.18f),
                    ClassificationResult("Q25234", 0.15f),
                ),
            )
    }
}
