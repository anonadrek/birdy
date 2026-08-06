package se.birdy.app.ui.onboarding

import se.birdy.datastore.AppLanguage

sealed interface OnboardingUiState {
    data object Loading : OnboardingUiState

    data class Visible(
        val pageIndex: Int,
        val nameInput: String,
        val selectedLanguage: AppLanguage? = null,
    ) : OnboardingUiState

    data object Done : OnboardingUiState
}
