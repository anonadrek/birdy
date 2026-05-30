package se.birdy.ml

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

sealed interface ClassifierBootstrapState {
    data object Initializing : ClassifierBootstrapState

    data class Ready(
        val classifier: BirdClassifier,
        val mode: ClassifierMode,
        val modelVersion: String?,
    ) : ClassifierBootstrapState

    data class Failed(
        val cause: Throwable,
    ) : ClassifierBootstrapState
}

class ClassifierBootstrap(
    private val buildClassifier: suspend () -> Triple<BirdClassifier, ClassifierMode, String?>,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    // Dispatcher för det tunga TFLite-bygget. Default = Default (off-main); injicerbar
    // så tester kan köra på test-schemaläggaren deterministiskt.
    private val buildContext: CoroutineContext = Dispatchers.Default,
) {
    private val _state = MutableStateFlow<ClassifierBootstrapState>(ClassifierBootstrapState.Initializing)
    val state: StateFlow<ClassifierBootstrapState> = _state.asStateFlow()

    init {
        launchBuild()
    }

    fun retry() {
        val current = _state.value
        if (current !is ClassifierBootstrapState.Failed) return
        if (!_state.compareAndSet(current, ClassifierBootstrapState.Initializing)) return
        launchBuild()
    }

    /** Cancels the in-flight build coroutine and closes any ready classifier. */
    fun close() {
        val current = _state.value
        scope.cancel()
        if (current is ClassifierBootstrapState.Ready) current.classifier.close()
    }

    private fun launchBuild() {
        scope.launch {
            try {
                val (clf, mode, version) = withContext(buildContext) { buildClassifier() }
                _state.value = ClassifierBootstrapState.Ready(clf, mode, version)
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                _state.value = ClassifierBootstrapState.Failed(t)
            }
        }
    }
}
