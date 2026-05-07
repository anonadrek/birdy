package se.birdy.ml

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
    suspend fun create(): Pair<BirdClassifier, ClassifierMode> =
        try {
            val real = createReal()
            val guarded =
                SessionFailureGuard(
                    real = real,
                    fallback = createFallback(),
                    threshold = sessionFailureThreshold,
                    onDegrade = onCrashlytics,
                )
            guarded to ClassifierMode.REAL
        } catch (t: Throwable) {
            onCrashlytics(t)
            createFallback() to ClassifierMode.DEMO
        }
}
