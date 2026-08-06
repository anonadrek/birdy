package se.birdy.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import se.birdy.app.i18n.toLocaleTagOrEmpty
import se.birdy.app.ui.settings.applyLocale
import se.birdy.datastore.AppLanguage
import se.birdy.datastore.UserPreferences

class OnboardingViewModel(
    private val prefs: UserPreferences,
    private val defaultFallbackName: String,
    private val isReplay: Boolean = false,
    private val applyLocaleFn: (String) -> Unit = ::applyLocale,
) : ViewModel() {
    private val _state =
        MutableStateFlow<OnboardingUiState>(
            OnboardingUiState.Visible(pageIndex = 0, nameInput = ""),
        )
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val stored = prefs.appLanguage.first()
            if (stored != AppLanguage.SYSTEM) {
                val current = _state.value
                if (current is OnboardingUiState.Visible && current.selectedLanguage == null) {
                    _state.value = current.copy(selectedLanguage = stored)
                }
            }
        }
    }

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

    /**
     * Eager persist BY DESIGN (inte i [complete]): (1) replay-lägets write-skip gäller
     * bara namn + hasSeenOnboarding — språket är en riktig inställning även i replay,
     * (2) värdet måste vara på disk INNAN AppCompat-recreaten läser om det.
     */
    fun selectLanguage(value: AppLanguage) {
        val current = _state.value as? OnboardingUiState.Visible ?: return
        _state.value = current.copy(selectedLanguage = value)
        viewModelScope.launch {
            prefs.setAppLanguage(value)
            applyLocaleFn(value.toLocaleTagOrEmpty())
        }
    }

    fun complete() {
        val current = _state.value
        if (current !is OnboardingUiState.Visible) return
        val trimmed = current.nameInput.trim()
        val resolvedName = trimmed.ifEmpty { defaultFallbackName }
        viewModelScope.launch {
            if (!isReplay) {
                prefs.setUserName(resolvedName)
                prefs.setHasSeenOnboarding(true)
            }
            _state.value = OnboardingUiState.Done
        }
    }

    private companion object {
        const val MAX_PAGE_INDEX = 7 // 8 pages: 0 (language), 1..7
    }
}
