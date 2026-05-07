package se.birdy.ml

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TfLiteBirdClassifier(
    private val info: BirdClassifierModelInfo,
    private val runner: TfliteRunner,
    private val preprocess: (ImageInput, BirdClassifierModelInfo) -> FloatArray,
    private val mapper: AiyLabelMapper,
    private val threshold: Float = 0.35f,
    private val topK: Int = 3,
) : BirdClassifier {
    private val mutex = Mutex()

    override suspend fun classify(image: ImageInput): Classification {
        val output = FloatArray(info.outputClasses)
        mutex.withLock {
            val input = preprocess(image, info)
            runner.run(input, output)
        }
        return Classification(
            results = topResults(output),
            frameTimestampMillis = image.timestampMillis,
        )
    }

    override fun close() {
        runner.close()
    }

    private fun topResults(scores: FloatArray): List<ClassificationResult> {
        require(scores.size == info.outputClasses) {
            "scores.size=${scores.size} != info.outputClasses=${info.outputClasses}"
        }
        val indexed =
            scores
                .mapIndexed { idx, score -> idx to score }
                .sortedByDescending { it.second }
        val out = mutableListOf<ClassificationResult>()
        for ((idx, score) in indexed) {
            if (score < threshold) break
            // mapper.lookup returns null for (a) the AIY background class (964) and
            // (b) class indices not present in aiy_to_qid.json — drop both silently.
            val qid = mapper.lookup(idx) ?: continue
            out += ClassificationResult(speciesId = qid, confidence = score)
            if (out.size == topK) break
        }
        return out
    }
}
