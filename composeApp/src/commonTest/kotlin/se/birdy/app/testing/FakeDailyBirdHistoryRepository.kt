package se.birdy.app.testing

import kotlinx.datetime.LocalDate
import se.birdy.data.dailybird.DailyBirdHistoryRepository

/**
 * In-memory [DailyBirdHistoryRepository] with the same rules as the SQL one: the first bird
 * recorded for a date sticks (INSERT OR IGNORE), and a match only counts when the saved species
 * is that date's bird. [failWith] makes every call throw, for degradation tests.
 */
class FakeDailyBirdHistoryRepository : DailyBirdHistoryRepository {
    val recorded = mutableMapOf<LocalDate, String>()
    val matched = mutableSetOf<LocalDate>()
    var failWith: Throwable? = null

    override suspend fun recordToday(
        date: LocalDate,
        speciesId: String,
    ) {
        failWith?.let { throw it }
        recorded.getOrPut(date) { speciesId }
    }

    override suspend fun speciesIdForDate(date: LocalDate): String? {
        failWith?.let { throw it }
        return recorded[date]
    }

    override suspend fun markMatch(
        date: LocalDate,
        observedSpeciesId: String,
    ) {
        failWith?.let { throw it }
        if (recorded[date] == observedSpeciesId) matched += date
    }

    override suspend fun totalMatchCount(): Int {
        failWith?.let { throw it }
        return matched.size
    }

    override suspend fun isMatched(date: LocalDate): Boolean {
        failWith?.let { throw it }
        return date in matched
    }
}
