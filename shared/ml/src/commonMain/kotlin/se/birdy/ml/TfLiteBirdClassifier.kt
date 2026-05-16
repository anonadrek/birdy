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
        // 0.05 — Plan 6b1 T8 Path B fallback (2026-05-16). The Phase 1 preprocessing
        // diagnos (docs/superpowers/research/2026-05-16-ml-preprocessing-diagnos.md)
        // refuted all 4 device-vs-desktop hypotheses, so the ~10% field hit-rate is
        // model-capacity-bound, not a preprocessing bug. Lowering the surface
        // threshold from 0.10 → 0.05 routes more borderline classifications into
        // Disambig instead of NoBird so the user-AI workflow handles the long tail.
        // ScanViewModel still applies 0.35 for the live top-1 chip; MatchThresholds
        // still routes ≥0.50 to Match and 0.05–0.50 to Disambig.
        const val DEFAULT_THRESHOLD: Float = 0.05f
        const val DEFAULT_TOP_K: Int = 3
    }
}
