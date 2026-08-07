package se.birdy.ml

import kotlinx.coroutines.CancellationException

enum class AudioClassifierMode { REAL, DEMO }

/**
 * Single entry-point for obtaining a [BirdAudioClassifier] at app startup.
 *
 * - If [createReal] succeeds the returned classifier is an [AudioSessionFailureGuard]-wrapped
 *   real model with [AudioClassifierMode.REAL] — unless [allowFallback] is false, in which case
 *   the real classifier is returned unwrapped (no guard, no [createFallback] call at all).
 * - If [createReal] throws (model missing, corrupt, native-lib failure) and [allowFallback] is
 *   true, the factory falls back to [createFallback] with [AudioClassifierMode.DEMO] and notifies
 *   [onDegrade] so the failure is visible in production monitoring (e.g. Crashlytics).
 * - If [allowFallback] is false, a [createReal] failure is rethrown after [onDegrade] instead of
 *   falling back — production builds must never silently answer every recording with
 *   [FakeAudioClassifier]'s canned guess (see the 16 KB-device classifier-load bug).
 */
class AudioClassifierFactory(
    private val createReal: suspend () -> BirdAudioClassifier,
    private val createFallback: () -> BirdAudioClassifier,
    private val onDegrade: (Throwable) -> Unit,
    private val sessionFailureThreshold: Int = 3,
    private val allowFallback: Boolean = true,
) {
    suspend fun create(): Pair<BirdAudioClassifier, AudioClassifierMode> {
        val real =
            try {
                createReal()
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                onDegrade(t)
                // Produktion (allowFallback=false): propagera ärligt fel istället för
                // att tyst svara "Koltrast 92%" på allt via FakeAudioClassifier.
                if (!allowFallback) throw t
                return createFallback() to AudioClassifierMode.DEMO
            }

        if (!allowFallback) return real to AudioClassifierMode.REAL

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
            onDegrade = onDegrade,
        ) to AudioClassifierMode.REAL
    }
}
