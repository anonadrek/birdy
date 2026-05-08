package se.birdy.app.ui.onboarding

sealed interface OnboardingUiState {
    data object Loading : OnboardingUiState

    data class Visible(
        val pageIndex: Int,
        val nameInput: String,
    ) : OnboardingUiState

    data object Done : OnboardingUiState
}
