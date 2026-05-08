package se.birdy.ml

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Drives the AIY Birds V1 inference pipeline: preprocess → runner → top-K with
 * threshold + Q-ID mapping. Mutex-serializes calls to [runner] so a single
 * instance can be shared across concurrent classify() callers.
 */
class TfLiteBirdClassifier(
    private val info: BirdClassifierModelInfo,
    private val runner: TfliteRunner,
    private val preprocess: (ImageInput, BirdClassifierModelInfo) -> FloatArray,
    private val mapper: AiyLabelMapper,
    private val threshold: Float = DEFAULT_THRESHOLD,
    private val topK: Int = DEFAULT_TOP_K,
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

    companion object {
        // 0.10 — empirically calibrated against AIY V1's per-class confidence.
        // The model spreads probability mass across visually similar species (e.g.
        // paridae tit cluster), so even a correct top-3 prediction often scores
        // 0.10–0.20 rather than ≥0.35. ScanViewModel applies a higher threshold
        // (0.35) for the live top-1 chip, so the conservative UX is preserved
        // while the freeze/result screen sees the full top-K candidate set.
        const val DEFAULT_THRESHOLD: Float = 0.10f
        const val DEFAULT_TOP_K: Int = 3
    }
}
