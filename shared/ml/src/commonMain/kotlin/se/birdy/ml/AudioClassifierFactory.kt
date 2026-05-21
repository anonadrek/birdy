package se.birdy.ml

import kotlinx.coroutines.CancellationException

enum class AudioClassifierMode { REAL, DEMO }

/**
 * Single entry-point for obtaining a [BirdAudioClassifier] at app startup.
 *
 * - If [createReal] succeeds the returned classifier is an [AudioSessionFailureGuard]-wrapped
 *   real model with [AudioClassifierMode.REAL].
 * - If [createReal] throws (model missing, corrupt, native-lib failure) the factory
 *   falls back to [createFallback] with [AudioClassifierMode.DEMO] and notifies
 *   [onCrashlytics] so the failure is visible in production monitoring.
 */
class AudioClassifierFactory(
    private val createReal: suspend () -> BirdAudioClassifier,
    private val createFallback: () -> BirdAudioClassifier,
    private val onCrashlytics: (Throwable) -> Unit,
    private val sessionFailureThreshold: Int = 3,
) {
    suspend fun create(): Pair<BirdAudioClassifier, AudioClassifierMode> {
        val real =
            try {
                createReal()
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                onCrashlytics(t)
                return createFallback() to AudioClassifierMode.DEMO
            }

        val fallback =
            try {
                createFallback()
            } catch (t: Throwable) {
                // Don't leak `real` if fallback creation itself fails.
                runCatching { real.close() }
                throw t
            }

        return AudioSessionFailureGuard(
            real = real,
            fallback = fallback,
            threshold = sessionFailureThreshold,
            onCrashlytics = onCrashlytics,
        ) to AudioClassifierMode.REAL
    }
}
