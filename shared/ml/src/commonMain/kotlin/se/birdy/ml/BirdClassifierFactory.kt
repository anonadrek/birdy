package se.birdy.ml

import kotlinx.coroutines.CancellationException

/**
 * Single entry-point for obtaining a [BirdClassifier] at app startup.
 *
 * - If [createReal] succeeds the returned classifier is a [SessionFailureGuard]-wrapped
 *   real model with [ClassifierMode.REAL].
 * - If [createReal] throws (model missing, corrupt, native-lib failure) the factory
 *   falls back to [createFallback] with [ClassifierMode.DEMO] and notifies
 *   [onCrashlytics] so the failure is visible in production monitoring.
 */
class BirdClassifierFactory(
    private val createReal: suspend () -> BirdClassifier,
    private val createFallback: () -> BirdClassifier,
    private val onCrashlytics: (Throwable) -> Unit,
    private val sessionFailureThreshold: Int = 3,
) {
    suspend fun create(): Pair<BirdClassifier, ClassifierMode> {
        val real =
            try {
                createReal()
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                onCrashlytics(t)
                return createFallback() to ClassifierMode.DEMO
            }

        val fallback =
            try {
                createFallback()
            } catch (t: Throwable) {
                // Don't leak `real` if fallback creation itself fails.
                runCatching { real.close() }
                throw t
            }

        return SessionFailureGuard(
            real = real,
            fallback = fallback,
            threshold = sessionFailureThreshold,
            onDegrade = onCrashlytics,
        ) to ClassifierMode.REAL
    }
}
