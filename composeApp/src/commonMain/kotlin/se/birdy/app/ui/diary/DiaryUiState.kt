package se.birdy.app.ui.diary

sealed interface DiaryUiState {
    data object Loading : DiaryUiState

    data object Empty : DiaryUiState

    data class Loaded(
        val months: List<MonthGroup>,
    ) : DiaryUiState
}

data class MonthGroup(
    val year: Int,
    val month1Based: Int,
    val items: List<DiaryItem>,
)

data class DiaryItem(
    val observationId: String,
    val speciesName: String,
    val photoPath: String,
    val confidencePct: Int,
    val relativeDate: RelativeDateText,
) {
    companion object {
        const val UNKNOWN_SPECIES_PLACEHOLDER = "__UNKNOWN_SPECIES__"
    }
}
