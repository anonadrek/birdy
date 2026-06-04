package se.birdy.app.ui.recap

import se.birdy.app.recap.WeeklyRecap

/** Ett namn-resolverat fynd för skärmen (en PlateFrame). */
data class RecapFindItem(
    val observationId: String,
    val speciesName: String?,
    val photoPath: String,
    val heroImagePath: String?,
    val isNewSpecies: Boolean,
)

sealed interface RecapUiState {
    data object Loading : RecapUiState

    data class Loaded(
        val recap: WeeklyRecap,
        val finds: List<RecapFindItem>,
        val newBadgeNames: List<String>,
    ) : RecapUiState

    data class Error(
        val kind: RecapErrorKind,
    ) : RecapUiState
}

enum class RecapErrorKind { LoadFailed }
