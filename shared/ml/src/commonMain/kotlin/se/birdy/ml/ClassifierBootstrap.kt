package se.birdy.ml

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
) {
    private val _state = MutableStateFlow<ClassifierBootstrapState>(ClassifierBootstrapState.Initializing)
    val state: StateFlow<ClassifierBootstrapState> = _state.asStateFlow()

    init {
        scope.launch {
            try {
                val (clf, mode, version) = withContext(Dispatchers.Default) { buildClassifier() }
                _state.value = ClassifierBootstrapState.Ready(clf, mode, version)
            } catch (t: Throwable) {
                _state.value = ClassifierBootstrapState.Failed(t)
            }
        }
    }

    fun retry() {
        if (_state.value !is ClassifierBootstrapState.Failed) return
        _state.value = ClassifierBootstrapState.Initializing
        scope.launch {
            try {
                val (clf, mode, version) = withContext(Dispatchers.Default) { buildClassifier() }
                _state.value = ClassifierBootstrapState.Ready(clf, mode, version)
            } catch (t: Throwable) {
                _state.value = ClassifierBootstrapState.Failed(t)
            }
        }
    }
}
