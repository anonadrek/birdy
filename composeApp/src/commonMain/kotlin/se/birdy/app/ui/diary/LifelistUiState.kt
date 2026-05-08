package se.birdy.app.ui.diary

import se.birdy.content.model.Species
import se.birdy.datastore.LifelistSort
import se.birdy.datastore.LifelistStat3Choice
import se.birdy.domain.observation.Observation

data class Stat3Value(
    val kind: LifelistStat3Choice,
    val value: Int,
)

data class LifelistRow(
    val observation: Observation,
    val species: Species?,
)

sealed interface LifelistUiState {
    data object Loading : LifelistUiState

    data object Empty : LifelistUiState

    data class Loaded(
        val userName: String,
        val speciesCount: Int,
        val stampsCount: Int,
        val daysActive: Int,
        val stat3: Stat3Value,
        val sort: LifelistSort,
        val rows: List<LifelistRow>,
    ) : LifelistUiState
}
