package se.birdy.app.ui.match

import kotlinx.datetime.Instant
import se.birdy.content.model.Species
import se.birdy.domain.badge.Badge
import se.birdy.domain.badge.BadgeUnlock
import se.birdy.ml.ScanSource

data class ResolvedPrediction(
    val species: Species,
    val confidence: Float,
)

sealed interface MatchResultUiState {
    data object Loading : MatchResultUiState

    /** Alla predictions under källans disambig-tröskel ([MatchThresholds]). Inget bevisat fågel-fynd. */
    data class NoBird(
        val frameJpegPath: String?,
        val capturedAtMs: Long,
        val source: ScanSource,
        val topPrediction: ResolvedPrediction? = null,
    ) : MatchResultUiState

    /** Top-1 i Disambig-bandet ([MatchThresholds], mellan disambig- och match-tröskeln). Användaren får välja bland 2-3 kandidater. */
    data class Disambig(
        val candidates: List<ResolvedPrediction>,
        val stampNumber: Int,
        val frameJpegPath: String?,
        val capturedAtMs: Long,
        val source: ScanSource,
        val saveStatus: SaveStatus = SaveStatus.NotSaved,
    ) : MatchResultUiState

    /** Top-1 ≥ källans match-tröskel ([MatchThresholds]) ELLER användaren just pickade från Disambig. */
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
        val source: ScanSource,
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
