package se.birdy.app.ui.recap

import kotlinx.datetime.LocalDate
import se.birdy.app.recap.WeeklyRecap

/** A find with its species name resolved: a square in the grid, a day in the strip, a new species row. */
data class RecapFindItem(
    val observationId: String,
    val speciesName: String?,
    val photoPath: String,
    val heroImagePath: String?,
    /** The local date the find was made, for the labels TalkBack reads. */
    val date: LocalDate,
    /** An audio find: its own image is a waveform, so the species' plate photo goes first. */
    val isHeard: Boolean = false,
)

/** One day of the Monday-to-Sunday strip. */
data class RecapDayItem(
    val date: LocalDate,
    val findCount: Int,
    /** The day's latest find, whose photo the strip shows. */
    val cover: RecapFindItem?,
    val isFuture: Boolean,
)

/** A species first found this week and its number in the life list ("№ 31 i livslistan"). */
data class RecapNewSpeciesItem(
    val lifeListNumber: Int,
    /** The find that put the species on the list. */
    val find: RecapFindItem,
)

/** A stamp earned this week, as the stamp card shows it. */
data class RecapStampItem(
    val badgeId: String,
    /** The stamp's number in the catalog, as on Märken ("№23"). */
    val stampNumber: Int,
    val name: String,
    val description: String,
    /** Premium stamps are brass, the others rust, as on Märken. */
    val isPremium: Boolean,
)

sealed interface RecapUiState {
    data object Loading : RecapUiState

    data class Loaded(
        val recap: WeeklyRecap,
        /** The week's finds, newest first. */
        val finds: List<RecapFindItem>,
        /** Monday to Sunday. */
        val days: List<RecapDayItem>,
        /** Latest addition to the life list first. */
        val newSpecies: List<RecapNewSpeciesItem>,
        /** Stamps earned this week, newest first. */
        val stamps: List<RecapStampItem>,
    ) : RecapUiState

    data class Error(
        val kind: RecapErrorKind,
    ) : RecapUiState
}

enum class RecapErrorKind { LoadFailed }
