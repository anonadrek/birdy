package se.birdy.app.ui.result

import se.birdy.content.model.Species
import se.birdy.domain.badge.Badge
import se.birdy.domain.badge.BadgeUnlock

data class ResolvedPrediction(
    val species: Species,
    val confidence: Float,
)

sealed interface ClassificationResultUiState {
    data object Loading : ClassificationResultUiState

    data class Loaded(
        val top1: ResolvedPrediction,
        val runnerUps: List<ResolvedPrediction>,
        val frozenFramePath: String?,
        val unresolved: List<String>,
        val saveStatus: SaveStatus = SaveStatus.NotSaved,
        val pendingUnlock: BadgeUnlock? = null,
        val pendingBadge: Badge? = null,
        val unlockQueueSize: Int = 0,
    ) : ClassificationResultUiState

    data class Error(
        val kind: Kind,
    ) : ClassificationResultUiState {
        enum class Kind { NoPredictions, NoMatches }
    }

    sealed interface SaveStatus {
        data object NotSaved : SaveStatus

        data object Saving : SaveStatus

        data object Saved : SaveStatus

        data class Failed(
            val kind: Kind,
        ) : SaveStatus {
            enum class Kind { PhotoEncodeFailed, StorageFull, DatabaseFailed, FrameUnavailable }
        }
    }
}
