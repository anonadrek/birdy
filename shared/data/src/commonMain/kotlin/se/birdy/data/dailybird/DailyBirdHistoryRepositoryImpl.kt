package se.birdy.data.dailybird

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import se.birdy.data.db.BirdyData

class DailyBirdHistoryRepositoryImpl(
    private val db: BirdyData,
) : DailyBirdHistoryRepository {
    private val queries get() = db.dailyBirdHistoryQueries

    override suspend fun recordToday(
        date: LocalDate,
        speciesId: String,
    ) = withContext(Dispatchers.Default) {
        queries.upsertCandidate(date = date.toString(), species_id = speciesId)
    }

    override suspend fun speciesIdForDate(date: LocalDate): String? =
        withContext(Dispatchers.Default) {
            queries.selectByDate(date.toString()).executeAsOneOrNull()?.species_id
        }

    override suspend fun markMatch(
        date: LocalDate,
        observedSpeciesId: String,
    ) = withContext(Dispatchers.Default) {
        queries.markMatched(date = date.toString(), species_id = observedSpeciesId)
    }

    override suspend fun totalMatchCount(): Int =
        withContext(Dispatchers.Default) {
            queries.countMatched().executeAsOne().toInt()
        }
}
