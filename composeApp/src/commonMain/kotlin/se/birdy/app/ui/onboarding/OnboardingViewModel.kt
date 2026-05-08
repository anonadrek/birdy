package se.birdy.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import se.birdy.datastore.UserPreferences

class OnboardingViewModel(
    private val prefs: UserPreferences,
    private val defaultFallbackName: String,
) : ViewModel() {
    private val _state =
        MutableStateFlow<OnboardingUiState>(
            OnboardingUiState.Visible(pageIndex = 0, nameInput = ""),
        )
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    fun setPageIndex(index: Int) {
        val current = _state.value
        if (current is OnboardingUiState.Visible) {
            _state.value = current.copy(pageIndex = index.coerceIn(0, MAX_PAGE_INDEX))
        }
    }

    fun onNameChange(value: String) {
        val current = _state.value
        if (current is OnboardingUiState.Visible) {
            _state.value = current.copy(nameInput = value)
        }
    }

    fun complete() {
        val current = _state.value
        if (current !is OnboardingUiState.Visible) return
        val trimmed = current.nameInput.trim()
        val resolvedName = trimmed.ifEmpty { defaultFallbackName }
        viewModelScope.launch {
            prefs.setUserName(resolvedName)
            prefs.setHasSeenOnboarding(true)
            _state.value = OnboardingUiState.Done
        }
    }

    private companion object {
        const val MAX_PAGE_INDEX = 2 // 3 pages: 0, 1, 2
    }
}
