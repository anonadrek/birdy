package se.birdy.app.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import se.birdy.content.Abundance
import se.birdy.content.Locale
import se.birdy.content.SpeciesFilter
import se.birdy.content.SpeciesId
import se.birdy.content.SpeciesRepository
import se.birdy.content.model.Species
import se.birdy.content.model.SpeciesSummary
import se.birdy.content.model.SpeciesTaxonomy

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

    companion object {
        private val allMonths =
            listOf("jan", "feb", "mar", "apr", "maj", "jun", "jul", "aug", "sep", "okt", "nov", "dec")
                .associateWith { "present" }

        /** Returns a repository pre-populated with the 5 species FakeBirdClassifier cycles through. */
        fun withDefaults(): FakeSpeciesRepository =
            FakeSpeciesRepository().apply {
                byId.value =
                    mapOf(
                        SpeciesId("Q25485") to
                            Species(
                                id = SpeciesId("Q25485"),
                                scientificName = "Parus major",
                                taxonomy =
                                    SpeciesTaxonomy(
                                        family = "Paridae",
                                        familySv = "Mesfåglar",
                                        genus = "Parus",
                                        iocOrder = "Passeriformes",
                                    ),
                                name = "Talgoxe",
                                abundance = Abundance.ALLMÄN,
                                iucnStatus = "LC",
                                regions = listOf("SE", "NO", "FI", "DK", "DE"),
                                season = allMonths,
                                description = "Talgoxen är en av Sveriges vanligaste tättingar.",
                                migration = "Mestadels stannfågel.",
                                images = emptyList(),
                            ),
                        SpeciesId("Q25234") to
                            Species(
                                id = SpeciesId("Q25234"),
                                scientificName = "Turdus merula",
                                taxonomy =
                                    SpeciesTaxonomy(
                                        family = "Turdidae",
                                        familySv = "Trastar",
                                        genus = "Turdus",
                                        iocOrder = "Passeriformes",
                                    ),
                                name = "Koltrast",
                                abundance = Abundance.ALLMÄN,
                                iucnStatus = "LC",
                                regions = listOf("SE", "NO", "FI", "DK", "DE"),
                                season = allMonths,
                                description = "Koltrasten är en av Sveriges vanligaste fåglar.",
                                migration = "Mestadels stannfågel.",
                                images = emptyList(),
                            ),
                        SpeciesId("Q25404") to
                            Species(
                                id = SpeciesId("Q25404"),
                                scientificName = "Cyanistes caeruleus",
                                taxonomy =
                                    SpeciesTaxonomy(
                                        family = "Paridae",
                                        familySv = "Mesfåglar",
                                        genus = "Cyanistes",
                                        iocOrder = "Passeriformes",
                                    ),
                                name = "Blåmes",
                                abundance = Abundance.ALLMÄN,
                                iucnStatus = "LC",
                                regions = listOf("SE", "NO", "FI", "DK", "DE"),
                                season = allMonths,
                                description = "Blåmesen är en liten, livlig mes.",
                                migration = "Mestadels stannfågel.",
                                images = emptyList(),
                            ),
                        SpeciesId("Q25402") to
                            Species(
                                id = SpeciesId("Q25402"),
                                scientificName = "Cygnus olor",
                                taxonomy =
                                    SpeciesTaxonomy(
                                        family = "Anatidae",
                                        familySv = "Andfåglar",
                                        genus = "Cygnus",
                                        iocOrder = "Anseriformes",
                                    ),
                                name = "Knölsvan",
                                abundance = Abundance.ALLMÄN,
                                iucnStatus = "LC",
                                regions = listOf("SE", "NO", "FI", "DK", "DE"),
                                season = allMonths,
                                description = "Knölsvanen är en välkänd stor svan.",
                                migration = "Mestadels stannfågel.",
                                images = emptyList(),
                            ),
                        SpeciesId("Q26490") to
                            Species(
                                id = SpeciesId("Q26490"),
                                scientificName = "Falco tinnunculus",
                                taxonomy =
                                    SpeciesTaxonomy(
                                        family = "Falconidae",
                                        familySv = "Falkar",
                                        genus = "Falco",
                                        iocOrder = "Falconiformes",
                                    ),
                                name = "Tornfalk",
                                abundance = Abundance.ALLMÄN,
                                iucnStatus = "LC",
                                regions = listOf("SE", "NO", "FI", "DK", "DE"),
                                season = allMonths,
                                description = "Tornfalken är en liten rovfågel.",
                                migration = "Delvis flyttfågel.",
                                images = emptyList(),
                            ),
                    )
            }
    }
}
