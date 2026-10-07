package se.birdy.content

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import se.birdy.content.db.BirdyContent
import se.birdy.content.model.Species
import se.birdy.content.model.SpeciesImage
import se.birdy.content.model.SpeciesSummary
import se.birdy.content.model.SpeciesTaxonomy
import se.birdy.content.search.SearchNames
import se.birdy.content.search.SearchRanking
import se.birdy.content.search.normalizeSearch

/**
 * [se.birdy.content.SpeciesText] kind for the name Birdy used before it took BirdLife Sverige's
 * official Swedish one (release 1.3.0 Task 7m), stored with the locale it belongs to. A text row
 * rather than a column: no schema change, and only renamed species have one (28: Diomedeslira's
 * former name is another species' name, so it is only a search term, see SpeciesDbBuilder).
 */
internal const val FORMER_NAME_KIND = "former_name"

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
            val marginalia = pickText(texts, locale, "marginalia")
            val formerName = texts.formerName(locale)

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
                            group = taxonomy.group_id,
                        ),
                    name = name,
                    abundance =
                        Abundance.fromCode(row.abundance) ?: Abundance.OVANLIG,
                    iucnStatus = row.iucn_status,
                    regions = regions,
                    season = seasons.associate { it.month to it.status },
                    description = description,
                    migration = migration,
                    marginalia = marginalia,
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
                    formerName = formerName,
                ),
            )
        }

    override fun search(
        query: String,
        locale: Locale,
        filters: SpeciesFilter,
    ): Flow<List<SpeciesSummary>> =
        db.speciesNameQueries
            .searchByNameOrScientific(query = normalizeSearch(query), max = Long.MAX_VALUE)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows ->
                val formerNames = db.formerNamesBySpecies(locale)
                val hits =
                    rows
                        .distinctBy { it.species_id }
                        .mapNotNull { row -> searchHit(row.species_id, locale, filters, formerNames[row.species_id]) }
                // Release 1.3.0 Task 7g: best matches first (see SearchRanking), not the SQL's
                // prefix-then-name order, which the screen re-sorted alphabetically anyway.
                SearchRanking
                    .rank(query = query, items = hits, names = { it.second }, abundance = { it.first.abundance })
                    .map { it.first }
            }
            // The per-hit queries and the ranking above run off the collector's (main) thread.
            .flowOn(Dispatchers.Default)

    /** One search result and the names it can be ranked by, or null when [filters] rule it out. */
    private fun searchHit(
        speciesId: String,
        locale: Locale,
        filters: SpeciesFilter,
        formerName: String?,
    ): Pair<SpeciesSummary, SearchNames>? {
        val sp =
            db.speciesQueries
                .selectById(speciesId)
                .executeAsOneOrNull()
                ?.takeIf { passesFilters(it.id, Abundance.fromCode(it.abundance) ?: Abundance.OVANLIG, filters) }
                ?: return null
        val abundance = Abundance.fromCode(sp.abundance) ?: Abundance.OVANLIG
        val taxonomy =
            db.speciesTaxonomyQueries
                .selectBySpecies(sp.id)
                .executeAsOneOrNull()
        // Search rows only carry the matched locale; fetch all to resolve the
        // display name in the user's locale (EN fallback matches getById/summaryFor).
        val nameRows = db.speciesNameQueries.selectBySpecies(sp.id).executeAsList()
        val displayName =
            nameRows.firstOrNull { it.locale == locale.code }?.name
                ?: nameRows.firstOrNull { it.locale == Locale.EN.code }?.name
                ?: sp.scientific_name
        val summary =
            SpeciesSummary(
                id = SpeciesId(sp.id),
                name = displayName,
                scientificName = sp.scientific_name,
                abundance = abundance,
                heroImagePath =
                    db.speciesImageQueries
                        .selectBySpecies(sp.id)
                        .executeAsList()
                        .firstOrNull { it.role == "hero" }
                        ?.path,
                iocOrder = taxonomy?.ioc_order ?: "",
                family = taxonomy?.family ?: "",
                familySv = taxonomy?.family_sv ?: "",
                group = taxonomy?.group_id ?: "",
                iucnStatus = sp.iucn_status,
            )
        val otherName = nameRows.firstOrNull { it.locale != locale.code }?.name
        return summary to
            SearchNames(primary = displayName, other = otherName, scientific = sp.scientific_name, former = formerName)
    }

    private fun passesFilters(
        speciesId: String,
        abundance: Abundance,
        filters: SpeciesFilter,
    ): Boolean {
        val abundanceOk = filters.abundance.isEmpty() || abundance in filters.abundance
        val regionOk =
            filters.regions.isEmpty() ||
                db.speciesRegionQueries
                    .selectBySpecies(speciesId)
                    .executeAsList()
                    .toSet()
                    .intersect(filters.regions)
                    .isNotEmpty()
        val monthOk =
            filters.activeInMonth == null ||
                db.speciesSeasonQueries
                    .selectBySpecies(speciesId)
                    .executeAsList()
                    .firstOrNull { it.month == filters.activeInMonth }
                    .let { month -> month != null && month.status != "absent" }
        return abundanceOk && regionOk && monthOk
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

    override suspend fun allByQid(locale: Locale): Map<SpeciesId, Species> =
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
                        names.firstOrNull { it.locale == locale.code }?.name
                            ?: names.firstOrNull { it.locale == Locale.EN.code }?.name
                            ?: row.scientific_name
                    val description = pickText(texts, locale, "description")
                    val migration = pickText(texts, locale, "migration")
                    val marginalia = pickText(texts, locale, "marginalia")
                    val formerName = texts.formerName(locale)

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
                                    group = taxonomy.group_id,
                                ),
                            name = name,
                            abundance = Abundance.fromCode(row.abundance) ?: Abundance.OVANLIG,
                            iucnStatus = row.iucn_status,
                            regions = regions,
                            season = seasons.associate { it.month to it.status },
                            description = description,
                            migration = migration,
                            marginalia = marginalia,
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
                            formerName = formerName,
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
        val taxonomy = db.speciesTaxonomyQueries.selectBySpecies(speciesId).executeAsOneOrNull()
        return SpeciesSummary(
            id = SpeciesId(sp.id),
            name = name,
            scientificName = sp.scientific_name,
            abundance = Abundance.fromCode(sp.abundance) ?: Abundance.OVANLIG,
            heroImagePath = hero,
            iocOrder = taxonomy?.ioc_order ?: "",
            family = taxonomy?.family ?: "",
            familySv = taxonomy?.family_sv ?: "",
            group = taxonomy?.group_id ?: "",
            iucnStatus = sp.iucn_status,
        )
    }

    /**
     * The [kind] text in [locale], cleaned by [cleanSpeciesText]. Falls back to the English text
     * when the localized one is missing OR cleans to blank (e.g. a Swedish "no data" sentinel);
     * a blank result makes the UI show its own localized empty-state string.
     */
    private fun pickText(
        texts: List<se.birdy.content.SpeciesText>,
        locale: Locale,
        kind: String,
    ): String? {
        val localized =
            texts
                .firstOrNull { it.locale == locale.code && it.kind == kind }
                ?.text
                ?.let(::cleanSpeciesText)
        if (!localized.isNullOrBlank()) return localized
        val english =
            texts
                .firstOrNull { it.locale == Locale.EN.code && it.kind == kind }
                ?.text
                ?.let(::cleanSpeciesText)
        return english ?: localized
    }
}

/**
 * The former name in [locale] only, no English fallback: an English user sees no "Formerly" line
 * for a Swedish rename.
 */
private fun List<SpeciesText>.formerName(locale: Locale): String? =
    firstOrNull { it.locale == locale.code && it.kind == FORMER_NAME_KIND }?.text

/**
 * Every renamed species' former name (one query; there are 29), preferring the one in [locale].
 * Only Swedish names were renamed, so an English user who types "sädgås" ranks by it too.
 */
private fun BirdyContent.formerNamesBySpecies(locale: Locale): Map<String, String> =
    speciesTextQueries
        .selectByKind(FORMER_NAME_KIND)
        .executeAsList()
        .groupBy { it.species_id }
        .mapValues { (_, rows) -> (rows.firstOrNull { it.locale == locale.code } ?: rows.first()).text }
