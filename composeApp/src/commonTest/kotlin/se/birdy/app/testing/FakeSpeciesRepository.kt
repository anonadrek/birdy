package se.birdy.app.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import se.birdy.content.Locale
import se.birdy.content.SpeciesFilter
import se.birdy.content.SpeciesId
import se.birdy.content.SpeciesRepository
import se.birdy.content.model.Species
import se.birdy.content.model.SpeciesSummary

class FakeSpeciesRepository : SpeciesRepository {
    val searchResults = MutableStateFlow<List<SpeciesSummary>>(emptyList())
    val byId = MutableStateFlow<Map<SpeciesId, Species?>>(emptyMap())
    val byFamily = MutableStateFlow<Map<String, List<SpeciesSummary>>>(emptyMap())
    val allList = MutableStateFlow<List<SpeciesSummary>>(emptyList())

    var lastSearchCall: Triple<String, Locale, SpeciesFilter>? = null

    override fun getById(
        id: SpeciesId,
        locale: Locale,
    ): Flow<Species?> = flow { emit(byId.value[id]) }

    override fun search(
        query: String,
        locale: Locale,
        filters: SpeciesFilter,
    ): Flow<List<SpeciesSummary>> {
        lastSearchCall = Triple(query, locale, filters)
        return searchResults.asStateFlow()
    }

    override fun listByFamily(
        familyKey: String,
        locale: Locale,
    ): Flow<List<SpeciesSummary>> = flowOf(byFamily.value[familyKey].orEmpty())

    override fun all(locale: Locale): Flow<List<SpeciesSummary>> = allList.asStateFlow()
}
