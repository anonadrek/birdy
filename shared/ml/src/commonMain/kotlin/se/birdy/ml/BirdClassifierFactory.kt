package se.birdy.ml

import kotlinx.coroutines.CancellationException

/**
 * Single entry-point for obtaining a [BirdClassifier] at app startup.
 *
 * - If [createReal] succeeds the returned classifier is a [SessionFailureGuard]-wrapped
 *   real model with [ClassifierMode.REAL] — unless [allowFallback] is false, in which case
 *   the real classifier is returned unwrapped (no guard, no [createFallback] call at all).
 * - If [createReal] throws (model missing, corrupt, native-lib failure) and [allowFallback] is
 *   true, the factory falls back to [createFallback] with [ClassifierMode.DEMO] and notifies
 *   [onCrashlytics] so the failure is visible in production monitoring.
 * - If [allowFallback] is false, a [createReal] failure is rethrown after [onCrashlytics]
 *   instead of falling back — production builds must never silently answer live-scan /
 *   gallery frames with [FakeBirdClassifier]'s canned guess (see the audio 16 KB-device
 *   load-failure trap; photo had the same mid-session SessionFailureGuard hole).
 */
class BirdClassifierFactory(
    private val createReal: suspend () -> BirdClassifier,
    private val createFallback: () -> BirdClassifier,
    private val onCrashlytics: (Throwable) -> Unit,
    private val sessionFailureThreshold: Int = 3,
    private val allowFallback: Boolean = true,
) {
    // Extra return (DEMO vs rethrow) and extra throw (allowFallback=false) are the
    // production honesty branches — same shape as AudioClassifierFactory.create.
    @Suppress("ReturnCount", "ThrowsCount")
    suspend fun create(): Pair<BirdClassifier, ClassifierMode> {
        val real =
            try {
                createReal()
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                onCrashlytics(t)
                // Produktion (allowFallback=false): propagera ärligt fel istället för
                // att tyst svara "Blåmes 87%" på allt via FakeBirdClassifier.
                if (!allowFallback) throw t
                return createFallback() to ClassifierMode.DEMO
            }

        if (!allowFallback) return real to ClassifierMode.REAL

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
