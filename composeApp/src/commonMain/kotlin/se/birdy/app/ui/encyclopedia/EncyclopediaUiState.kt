package se.birdy.app.ui.encyclopedia

import se.birdy.content.model.SpeciesSummary

data class GroupedSpecies(
    val common: List<SpeciesSummary>,
    val others: List<SpeciesSummary>,
)

sealed interface EncyclopediaUiState {
    data object Loading : EncyclopediaUiState

    data class Loaded(
        val grouped: GroupedSpecies,
        val sectionCommonHeader: String,
    ) : EncyclopediaUiState

    data object Empty : EncyclopediaUiState
}
