package se.birdy.ml

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Parallel to [SessionFailureGuard] for [BirdAudioClassifier].
 *
 * Wraps a real [BirdAudioClassifier] and degrades to [fallback] after [threshold]
 * consecutive [classify] failures. A successful call resets the failure counter.
 * Once degraded the guard stays in [AudioClassifierMode.DEMO] for the session.
 *
 * [onCrashlytics] is called exactly once when the threshold is crossed.
 *
 * Kept as a parallel class (not a generic refactor of the existing [SessionFailureGuard])
 * to avoid Plan 4b regression risk — the copy-then-evolve pattern is intentional.
 */
class AudioSessionFailureGuard(
    private val real: BirdAudioClassifier,
    private val fallback: BirdAudioClassifier,
    private val threshold: Int = 3,
    private val onCrashlytics: (Throwable) -> Unit = {},
) : BirdAudioClassifier {
    private val stateMutex = Mutex()
    private var failures: Int = 0
    private var degraded: Boolean = false

    val mode: AudioClassifierMode
        get() = if (degraded) AudioClassifierMode.DEMO else AudioClassifierMode.REAL

    override val info: AudioModelInfo
        get() = if (degraded) fallback.info else real.info

    override suspend fun classify(input: AudioInput): AudioClassification {
        val alreadyDegraded = stateMutex.withLock { degraded }
        if (alreadyDegraded) return fallback.classify(input)
        return try {
            val result = real.classify(input)
            stateMutex.withLock { failures = 0 }
            result
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            val justDegraded =
                stateMutex.withLock {
                    failures += 1
                    val now = failures > threshold && !degraded
                    if (now) degraded = true
                    now
                }
            if (justDegraded) {
                onCrashlytics(t)
                fallback.classify(input)
            } else {
                throw t
            }
        }
    }

    override fun close() {
        try {
            real.close()
        } finally {
            fallback.close()
        }
    }
}
