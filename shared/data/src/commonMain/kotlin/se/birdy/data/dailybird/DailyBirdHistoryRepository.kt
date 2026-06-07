package se.birdy.data.dailybird

import kotlinx.datetime.LocalDate

interface DailyBirdHistoryRepository {
    suspend fun recordToday(
        date: LocalDate,
        speciesId: String,
    )

    suspend fun speciesIdForDate(date: LocalDate): String?

    suspend fun markMatch(
        date: LocalDate,
        observedSpeciesId: String,
    )

    suspend fun totalMatchCount(): Int

    /** Whether the daily bird for [date] has been caught (matched). */
    suspend fun isMatched(date: LocalDate): Boolean
}
