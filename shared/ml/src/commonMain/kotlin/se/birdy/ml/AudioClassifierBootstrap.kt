package se.birdy.ml

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate

/**
 * Possible states of the audio classifier lifecycle.
 *
 * - [Idle] — no load attempt has been made (initial state; also after [AudioClassifierBootstrap.release]).
 * - [Loading] — [AudioClassifierBootstrap.ensureReady] is in progress.
 * - [Ready] — the classifier is available; [ready.classifier] is safe to call.
 * - [Failed] — loading threw; [failed.cause] carries the exception.
 *
 * UI should gate audio-scan entry behind `state is Ready`.
 */
sealed interface AudioClassifierBootstrapState {
    data object Idle : AudioClassifierBootstrapState

    data object Loading : AudioClassifierBootstrapState

    data class Ready(
        val classifier: BirdAudioClassifier,
        val mode: AudioClassifierMode,
    ) : AudioClassifierBootstrapState

    data class Failed(
        val cause: Throwable,
    ) : AudioClassifierBootstrapState
}

/**
 * Lazy holder for a [BirdAudioClassifier] that is built on first demand via [ensureReady].
 *
 * The bootstrap itself is NOT thread-safe (concurrent [ensureReady] calls may both trigger
 * a build). Thread-safety for the concurrent-load case is delegated to the
 * `AtomicReference<Deferred<...>>` + CAS pattern in [MainActivity] — only one [Deferred]
 * survives the CAS race and calls [ensureReady]; any losing [Deferred] is cancelled before
 * calling in. iOS has its own CAS mirror, `IosAudioBootstrap` (composeApp iosMain, since
 * i3), with the same Deferred-CAS-cache shape — this class itself remains an unused
 * building block on both platforms (see [release]'s KDoc for the same note).
 *
 * Contrast with [ClassifierBootstrap] (image model): that class starts loading eagerly
 * via `init { launchBuild() }` because the image model is always needed. The audio model
 * is heavy (57 MB) and free users never trigger audio-scan, so lazy on-demand loading is
 * the right trade-off here.
 *
 * @param build A suspend lambda that returns a [Pair] of classifier + mode.
 *   Typically wraps [AudioClassifierFactory.create].
 */
class AudioClassifierBootstrap(
    private val build: suspend () -> Pair<BirdAudioClassifier, AudioClassifierMode>,
) {
    private val _state =
        MutableStateFlow<AudioClassifierBootstrapState>(AudioClassifierBootstrapState.Idle)

    /** Observable lifecycle state. Collect in UI to show loading/error indicators. */
    val state: StateFlow<AudioClassifierBootstrapState> = _state.asStateFlow()

    /**
     * Ensures the classifier is ready, loading it if necessary.
     *
     * - If already [AudioClassifierBootstrapState.Ready], returns the existing classifier immediately.
     * - If [AudioClassifierBootstrapState.Idle] or [AudioClassifierBootstrapState.Failed], starts (or retries) the build.
     * - If the build succeeds, transitions to [AudioClassifierBootstrapState.Ready] and returns the classifier.
     * - If the build throws, transitions to [AudioClassifierBootstrapState.Failed] and rethrows.
     *
     * [AudioClassifierBootstrapState.Loading] is an intermediate state visible to UI during the build.
     */
    suspend fun ensureReady(): BirdAudioClassifier {
        val current = _state.value
        if (current is AudioClassifierBootstrapState.Ready) return current.classifier
        _state.value = AudioClassifierBootstrapState.Loading
        return try {
            val (clf, mode) = build()
            _state.value = AudioClassifierBootstrapState.Ready(clf, mode)
            clf
        } catch (t: Throwable) {
            _state.value = AudioClassifierBootstrapState.Failed(t)
            throw t
        }
    }

    /**
     * Releases the classifier if ready and resets state to [AudioClassifierBootstrapState.Idle].
     *
     * [release] is unwired in production: `MainActivity` never constructs an
     * [AudioClassifierBootstrap] instance — it manages its own `Deferred`-backed
     * `audioBootstrapCache` directly and closes the classifier itself in `onDestroy`.
     * This method stays as a tested, ready-to-use building block for a future caller
     * that adopts this class instead.
     *
     * Uses [getAndUpdate] for atomicity: the state is swapped to [AudioClassifierBootstrapState.Idle]
     * in a single CAS operation, so a concurrent [ensureReady] cannot observe a half-released
     * state (Fix #6). Tests pin its contract.
     */
    fun release() {
        val previous = _state.getAndUpdate { AudioClassifierBootstrapState.Idle }
        (previous as? AudioClassifierBootstrapState.Ready)?.classifier?.close()
    }
}
