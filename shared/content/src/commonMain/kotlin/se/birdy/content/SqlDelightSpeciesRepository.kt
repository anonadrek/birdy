package se.birdy.content

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import se.birdy.content.db.BirdyContent
import se.birdy.content.model.Species
import se.birdy.content.model.SpeciesImage
import se.birdy.content.model.SpeciesSummary
import se.birdy.content.model.SpeciesTaxonomy

@Suppress("LongMethod")
class SqlDelightSpeciesRepository(
    private val db: BirdyContent,
) : SpeciesRepository {
    override fun getById(
        id: SpeciesId,
        locale: Locale,
    ): Flow<Species?> =
        flow {
            val row =
                db.speciesQueries
                    .selectById(id.raw)
                    .executeAsOneOrNull()
            if (row == null) {
                emit(null)
                return@flow
            }
            val taxonomy =
                db.speciesTaxonomyQueries
                    .selectBySpecies(id.raw)
                    .executeAsOne()
            val names =
                db.speciesNameQueries.selectBySpecies(id.raw).executeAsList()
            val texts =
                db.speciesTextQueries.selectBySpecies(id.raw).executeAsList()
            val regions =
                db.speciesRegionQueries.selectBySpecies(id.raw).executeAsList()
            val seasons =
                db.speciesSeasonQueries.selectBySpecies(id.raw).executeAsList()
            val images =
                db.speciesImageQueries.selectBySpecies(id.raw).executeAsList()

            val name =
                names.firstOrNull { it.locale == locale.code }?.name
                    ?: names.firstOrNull { it.locale == Locale.EN.code }?.name
                    ?: row.scientific_name
            val description = pickText(texts, locale, "description")
            val migration = pickText(texts, locale, "migration")

            emit(
                Species(
                    id = id,
                    scientificName = row.scientific_name,
                    taxonomy =
                        SpeciesTaxonomy(
                            family = taxonomy.family,
                            familySv = taxonomy.family_sv,
                            genus = taxonomy.genus,
                            iocOrder = taxonomy.ioc_order,
                        ),
                    name = name,
                    abundance =
                        Abundance.fromCode(row.abundance) ?: Abundance.OVANLIG,
                    iucnStatus = row.iucn_status,
                    regions = regions,
                    season = seasons.associate { it.month to it.status },
                    description = description,
                    migration = migration,
                    images =
                        images.map { img ->
                            SpeciesImage(
                                role = img.role,
                                path = img.path,
                                width = img.width.toInt(),
                                height = img.height.toInt(),
                                license = img.license,
                                author = img.author,
                                sourceUrl = img.source_url,
                            )
                        },
                ),
            )
        }

    override fun search(
        query: String,
        locale: Locale,
        filters: SpeciesFilter,
    ): Flow<List<SpeciesSummary>> =
        db.speciesNameQueries
            .searchByNameOrScientific(locale = locale.code, query = query, max = Long.MAX_VALUE)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows ->
                rows
                    .distinctBy { it.species_id }
                    .mapNotNull { name ->
                        val sp =
                            db.speciesQueries
                                .selectById(name.species_id)
                                .executeAsOneOrNull() ?: return@mapNotNull null
                        val abundance =
                            Abundance.fromCode(sp.abundance) ?: Abundance.OVANLIG
                        if (filters.abundance.isNotEmpty() && abundance !in filters.abundance) {
                            return@mapNotNull null
                        }
                        if (filters.regions.isNotEmpty()) {
                            val speciesRegions =
                                db.speciesRegionQueries
                                    .selectBySpecies(sp.id)
                                    .executeAsList()
                                    .toSet()
                            if (filters.regions.intersect(speciesRegions).isEmpty()) {
                                return@mapNotNull null
                            }
                        }
                        if (filters.activeInMonth != null) {
                            val seasons =
                                db.speciesSeasonQueries.selectBySpecies(sp.id).executeAsList()
                            val month = seasons.firstOrNull { it.month == filters.activeInMonth }
                            if (month == null || month.status == "absent") {
                                return@mapNotNull null
                            }
                        }
                        SpeciesSummary(
                            id = SpeciesId(sp.id),
                            name = name.name,
                            scientificName = sp.scientific_name,
                            abundance = abundance,
                            heroImagePath =
                                db.speciesImageQueries
                                    .selectBySpecies(sp.id)
                                    .executeAsList()
                                    .firstOrNull { it.role == "hero" }
                                    ?.path,
                        )
                    }
            }

    override fun listByFamily(
        familyKey: String,
        locale: Locale,
    ): Flow<List<SpeciesSummary>> =
        flow {
            val ids =
                db.speciesTaxonomyQueries
                    .selectByFamily(familyKey)
                    .executeAsList()
            emit(ids.mapNotNull { speciesId -> summaryFor(speciesId, locale) })
        }

    override fun all(locale: Locale): Flow<List<SpeciesSummary>> =
        flow {
            val rows = db.speciesQueries.selectAll().executeAsList()
            emit(rows.mapNotNull { summaryFor(it.id, locale) })
        }

    override fun observeTotalCount(): Flow<Int> =
        db.speciesQueries
            .count()
            .asFlow()
            .mapToOne(Dispatchers.Default)
            .map { it.toInt() }

    override suspend fun allByQid(): Map<SpeciesId, Species> =
        withContext(Dispatchers.Default) {
            // Bulk-fetch: 6 queries total regardless of species count (vs N×6 previously)
            val allSpecies = db.speciesQueries.selectAll().executeAsList()
            val taxonomyById =
                db.speciesTaxonomyQueries
                    .selectAll()
                    .executeAsList()
                    .associateBy { it.species_id }
            val namesBySpecies =
                db.speciesNameQueries
                    .selectAll()
                    .executeAsList()
                    .groupBy { it.species_id }
            val textsBySpecies =
                db.speciesTextQueries
                    .selectAll()
                    .executeAsList()
                    .groupBy { it.species_id }
            val regionsBySpecies =
                db.speciesRegionQueries
                    .selectAll()
                    .executeAsList()
                    .groupBy { it.species_id }
            val seasonsBySpecies =
                db.speciesSeasonQueries
                    .selectAll()
                    .executeAsList()
                    .groupBy { it.species_id }
            val imagesBySpecies =
                db.speciesImageQueries
                    .selectAll()
                    .executeAsList()
                    .groupBy { it.species_id }

            allSpecies
                .mapNotNull { row ->
                    val taxonomy = taxonomyById[row.id] ?: return@mapNotNull null
                    val names = namesBySpecies[row.id].orEmpty()
                    val texts = textsBySpecies[row.id].orEmpty()
                    val regions = regionsBySpecies[row.id].orEmpty().map { it.region_iso }
                    val seasons = seasonsBySpecies[row.id].orEmpty()
                    val images = imagesBySpecies[row.id].orEmpty()

                    val name =
                        names.firstOrNull { it.locale == Locale.SV.code }?.name
                            ?: names.firstOrNull { it.locale == Locale.EN.code }?.name
                            ?: row.scientific_name
                    val description = pickText(texts, Locale.SV, "description")
                    val migration = pickText(texts, Locale.SV, "migration")

                    SpeciesId(row.id) to
                        Species(
                            id = SpeciesId(row.id),
                            scientificName = row.scientific_name,
                            taxonomy =
                                SpeciesTaxonomy(
                                    family = taxonomy.family,
                                    familySv = taxonomy.family_sv,
                                    genus = taxonomy.genus,
                                    iocOrder = taxonomy.ioc_order,
                                ),
                            name = name,
                            abundance = Abundance.fromCode(row.abundance) ?: Abundance.OVANLIG,
                            iucnStatus = row.iucn_status,
                            regions = regions,
                            season = seasons.associate { it.month to it.status },
                            description = description,
                            migration = migration,
                            images =
                                images.map { img ->
                                    SpeciesImage(
                                        role = img.role,
                                        path = img.path,
                                        width = img.width.toInt(),
                                        height = img.height.toInt(),
                                        license = img.license,
                                        author = img.author,
                                        sourceUrl = img.source_url,
                                    )
                                },
                        )
                }.toMap()
        }

    private fun summaryFor(
        speciesId: String,
        locale: Locale,
    ): SpeciesSummary? {
        val sp = db.speciesQueries.selectById(speciesId).executeAsOneOrNull() ?: return null
        val names = db.speciesNameQueries.selectBySpecies(speciesId).executeAsList()
        val name =
            names.firstOrNull { it.locale == locale.code }?.name
                ?: names.firstOrNull { it.locale == Locale.EN.code }?.name
                ?: sp.scientific_name
        val hero =
            db.speciesImageQueries
                .selectBySpecies(speciesId)
                .executeAsList()
                .firstOrNull { it.role == "hero" }
                ?.path
        return SpeciesSummary(
            id = SpeciesId(sp.id),
            name = name,
            scientificName = sp.scientific_name,
            abundance = Abundance.fromCode(sp.abundance) ?: Abundance.OVANLIG,
            heroImagePath = hero,
        )
    }

    private fun pickText(
        texts: List<se.birdy.content.SpeciesText>,
        locale: Locale,
        kind: String,
    ): String? {
        val match = texts.firstOrNull { it.locale == locale.code && it.kind == kind }
        if (match != null) return match.text
        return texts.firstOrNull { it.locale == Locale.EN.code && it.kind == kind }?.text
    }
}
