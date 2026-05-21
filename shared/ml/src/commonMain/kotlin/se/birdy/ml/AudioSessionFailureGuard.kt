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
 * [onDegrade] is called exactly once when the threshold is crossed (e.g. to log
 * to Crashlytics). State is coordinated via [Mutex] to match the repo pattern
 * (no atomicfu dependency configured in :shared:ml).
 *
 * The whole class assumes a single-threaded caller (the audio pipeline). Concurrent
 * [classify] invocations could result in lost failure increments around the success-path
 * reset. Only the throwable that crosses the threshold reaches [onDegrade]; earlier
 * failures (1..threshold-1) are rethrown to the caller.
 *
 * Kept as a parallel class (not a generic refactor of the existing [SessionFailureGuard])
 * to avoid Plan 4b regression risk — the copy-then-evolve pattern is intentional.
 */
class AudioSessionFailureGuard(
    private val real: BirdAudioClassifier,
    private val fallback: BirdAudioClassifier,
    private val threshold: Int = 3,
    private val onDegrade: (Throwable) -> Unit = {},
) : BirdAudioClassifier {
    private val stateMutex = Mutex()
    private var failures: Int = 0
    private var degraded: Boolean = false

    /**
     * Reads the current mode. Safe to call between [classify] invocations on a
     * single-threaded audio pipeline (the only caller in v1). For concurrent
     * readers, callers should serialize with [classify] externally.
     */
    val mode: AudioClassifierMode
        get() = if (degraded) AudioClassifierMode.DEMO else AudioClassifierMode.REAL

    /** Routes to [real] or [fallback] based on degradation state. Same single-threaded-caller
     * assumption as [mode]; concurrent readers should serialize externally. */
    private val current: BirdAudioClassifier
        get() = if (degraded) fallback else real

    override val info: AudioModelInfo
        get() = current.info

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
                onDegrade(t)
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
