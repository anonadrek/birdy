package se.birdy.app.ui.match

import kotlinx.datetime.Instant
import se.birdy.content.model.Species
import se.birdy.domain.badge.Badge
import se.birdy.domain.badge.BadgeUnlock

data class ResolvedPrediction(
    val species: Species,
    val confidence: Float,
)

sealed interface MatchResultUiState {
    data object Loading : MatchResultUiState

    /** Alla predictions < DISAMBIG_CONFIDENCE (0.35). Inget bevisat fågel-fynd. */
    data class NoBird(
        val frameJpegPath: String?,
        val capturedAtMs: Long,
        val topPrediction: ResolvedPrediction? = null,
    ) : MatchResultUiState

    /** Top-1 i 0.35–0.50-bandet. Användaren får välja bland 2-3 kandidater. */
    data class Disambig(
        val candidates: List<ResolvedPrediction>,
        val stampNumber: Int,
        val frameJpegPath: String?,
        val capturedAtMs: Long,
    ) : MatchResultUiState

    /** Top-1 ≥ 0.50 ELLER användaren just pickade från Disambig. */
    data class Match(
        val species: Species,
        val confidence: Float,
        val isManualPick: Boolean,
        val isFirstSighting: Boolean,
        val prevObservedAt: Instant?,
        val sightingCount: Int,
        val stampNumber: Int,
        val frameJpegPath: String?,
        val capturedAtMs: Long,
        val saveStatus: SaveStatus = SaveStatus.NotSaved,
        val pendingUnlock: BadgeUnlock? = null,
        val pendingBadge: Badge? = null,
        val unlockQueueSize: Int = 0,
    ) : MatchResultUiState

    /** Klassning misslyckades (parse-fel eller ingen art-resolution alls). */
    data class Error(
        val kind: Kind,
    ) : MatchResultUiState {
        enum class Kind { NoPredictions, ParseFailed }
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
