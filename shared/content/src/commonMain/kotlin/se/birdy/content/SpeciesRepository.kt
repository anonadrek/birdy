package se.birdy.content

import kotlinx.coroutines.flow.Flow
import se.birdy.content.model.Species
import se.birdy.content.model.SpeciesSummary

interface SpeciesRepository {
    fun getById(
        id: SpeciesId,
        locale: Locale,
    ): Flow<Species?>

    fun search(
        query: String,
        locale: Locale,
        filters: SpeciesFilter = SpeciesFilter(),
    ): Flow<List<SpeciesSummary>>

    fun listByFamily(
        familyKey: String,
        locale: Locale,
    ): Flow<List<SpeciesSummary>>

    fun all(locale: Locale): Flow<List<SpeciesSummary>>

    /** Antal arter i katalogen (oberoende av locale). Används av Badges-fliken. */
    fun observeTotalCount(): Flow<Int>

    /**
     * Engångs-snapshot av hela katalogen för rule-engine-evaluering.
     * Locale påverkar inte resultaten — [Species.taxonomy] och [Species.abundance] är språkneutrala.
     */
    suspend fun allByQid(): Map<SpeciesId, Species>
}
