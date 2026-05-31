package se.birdy.app.ui.recap

import se.birdy.app.recap.WeeklyRecap

sealed interface RecapUiState {
    data object Loading : RecapUiState

    data class Loaded(
        val recap: WeeklyRecap,
        val heroSpeciesName: String?,
        val newBadgeNames: List<String>,
    ) : RecapUiState

    data class Error(
        val kind: RecapErrorKind,
    ) : RecapUiState
}

enum class RecapErrorKind { LoadFailed }
