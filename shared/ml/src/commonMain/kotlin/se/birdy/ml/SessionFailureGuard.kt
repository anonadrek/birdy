package se.birdy.ml

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Wraps a real [BirdClassifier] and degrades to [fallback] after [threshold]
 * consecutive [classify] failures. A successful call resets the failure counter.
 * Once degraded the guard stays in [ClassifierMode.DEMO] for the session.
 *
 * [onDegrade] is called exactly once when the threshold is crossed (e.g. to log
 * to Crashlytics). State is coordinated via [Mutex] to match the repo pattern
 * (no atomicfu dependency configured in :shared:ml).
 */
class SessionFailureGuard(
    private val real: BirdClassifier,
    private val fallback: BirdClassifier,
    private val threshold: Int = 3,
    private val onDegrade: (Throwable) -> Unit = {},
) : BirdClassifier {
    private val stateMutex = Mutex()
    private var failures: Int = 0
    private var degraded: Boolean = false

    /**
     * Reads the current mode. Safe to call between [classify] invocations on a
     * single-threaded camera pipeline (the only caller in v1). For concurrent
     * readers, callers should serialize with [classify] externally.
     */
    val mode: ClassifierMode
        get() = if (degraded) ClassifierMode.DEMO else ClassifierMode.REAL

    override suspend fun classify(image: ImageInput): Classification {
        // Snapshot inside the mutex so we don't race with a concurrent failure-degrade.
        val alreadyDegraded = stateMutex.withLock { degraded }
        if (alreadyDegraded) return fallback.classify(image)
        return try {
            val result = real.classify(image)
            stateMutex.withLock { failures = 0 }
            result
        } catch (t: Throwable) {
            val (_, justDegraded) =
                stateMutex.withLock {
                    failures += 1
                    val degradeNow = failures > threshold && !degraded
                    if (degradeNow) degraded = true
                    failures to degradeNow
                }
            if (justDegraded) {
                onDegrade(t)
                fallback.classify(image)
            } else {
                throw t
            }
        }
    }

    override fun close() {
        real.close()
        fallback.close()
    }
}
