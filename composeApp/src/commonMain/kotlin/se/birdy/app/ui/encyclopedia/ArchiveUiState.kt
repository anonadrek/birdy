package se.birdy.app.ui.encyclopedia

import se.birdy.content.model.SpeciesSummary
import se.birdy.datastore.ArchiveSort

data class ArchiveRow(
    val summary: SpeciesSummary,
    val isStamped: Boolean,
    val stampNumber: Int? = null,
)

sealed interface ArchiveUiState {
    data object Loading : ArchiveUiState

    data object Empty : ArchiveUiState

    data class Loaded(
        val rows: List<ArchiveRow>,
        val sort: ArchiveSort,
    ) : ArchiveUiState

    data class Error(val message: String?) : ArchiveUiState
}
