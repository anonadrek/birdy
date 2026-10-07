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
import se.birdy.content.model.PhotoCredit
import se.birdy.content.model.Species
import se.birdy.content.model.SpeciesImage
import se.birdy.content.model.SpeciesSummary
import se.birdy.content.model.SpeciesTaxonomy
import se.birdy.content.model.SpeciesTextSource
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

// TooManyFunctions: the repository is the one place that reads species.db, by design; the photo
// credits (release 1.3.0 Task 7e-2) are two more queries on the same tables.
@Suppress("LongMethod", "TooManyFunctions")
class SqlDelightSpeciesRepository(
    private val db: BirdyContent,
) : SpeciesRepository {
    // Species content does not change while the app runs, so the former names (28 rows) are read
    // once per locale and reused by every search keystroke.
    private val formerNamesByLocale: Map<Locale, Lazy<Map<String, String>>> =
        Locale.entries.associateWith { locale -> lazy { db.formerNamesBySpecies(locale) } }

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
            val shown = ShownTexts.pick(texts, locale, id, row.wikipedia_sv_revision, row.wikipedia_en_revision)
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
                    description = shown.description,
                    migration = shown.migration,
                    marginalia = shown.marginalia,
                    images = images.map { it.toSpeciesImage() },
                    formerName = formerName,
                    textSources = shown.sources,
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
                val formerNames = formerNamesByLocale.getValue(locale).value
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
                    val shown =
                        ShownTexts.pick(
                            texts = texts,
                            locale = locale,
                            speciesId = SpeciesId(row.id),
                            svRevision = row.wikipedia_sv_revision,
                            enRevision = row.wikipedia_en_revision,
                        )
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
                            description = shown.description,
                            migration = shown.migration,
                            marginalia = shown.marginalia,
                            images = images.map { it.toSpeciesImage() },
                            formerName = formerName,
                            textSources = shown.sources,
                        )
                }.toMap()
        }

    override suspend fun photoCredits(locale: Locale): List<PhotoCredit> =
        withContext(Dispatchers.Default) {
            db.speciesImageQueries
                .selectCredits(locale = locale.code)
                .executeAsList()
                .map { row ->
                    PhotoCredit(
                        speciesId = SpeciesId(row.species_id),
                        speciesName = row.local_name ?: row.english_name ?: row.scientific_name,
                        scientificName = row.scientific_name,
                        role = row.role,
                        path = row.path,
                        license = row.license,
                        author = row.author,
                        commonsFileName = row.commons_filename,
                    )
                }
        }

    override suspend fun photoCount(): Int =
        withContext(Dispatchers.Default) {
            db.speciesImageQueries
                .countAll()
                .executeAsOne()
                .toInt()
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
}

private fun se.birdy.content.SpeciesImage.toSpeciesImage(): SpeciesImage =
    SpeciesImage(
        role = role,
        path = path,
        width = width.toInt(),
        height = height.toInt(),
        license = license,
        author = author,
        sourceUrl = source_url,
        commonsFileName = commons_filename,
    )

/**
 * A species' description, migration and marginalia as the app shows them, and the Wikipedia
 * article versions they were written from (release 1.3.0, 7i-fix A): one source per language
 * among the texts actually shown, in the order description, migration, marginalia. A text in
 * the English fallback credits the English article; a blank text (the UI shows its own
 * empty-state line) credits nothing.
 */
private class ShownTexts(
    val description: String?,
    val migration: String?,
    val marginalia: String?,
    val sources: List<SpeciesTextSource>,
) {
    companion object {
        fun pick(
            texts: List<SpeciesText>,
            locale: Locale,
            speciesId: SpeciesId,
            svRevision: String?,
            enRevision: String?,
        ): ShownTexts {
            val picked = listOf("description", "migration", "marginalia").map { pickText(texts, locale, it) }
            val sources =
                picked
                    .filter { !it.text.isNullOrBlank() }
                    .map { it.language }
                    .distinct()
                    .map { language ->
                        val revision =
                            when (language) {
                                Locale.SV -> svRevision
                                Locale.EN -> enRevision
                            }
                        SpeciesTextSource(language, revision, WikipediaLinks.articleUrl(language, revision, speciesId))
                    }
            return ShownTexts(picked[0].text, picked[1].text, picked[2].text, sources)
        }
    }
}

/** One text as shown, and the language it is in (English when it is the fallback). */
private data class PickedText(
    val text: String?,
    val language: Locale,
)

/**
 * The [kind] text in [locale], cleaned by [cleanSpeciesText]. Falls back to the English text
 * when the localized one is missing OR cleans to blank (e.g. a Swedish "no data" sentinel);
 * a blank result makes the UI show its own localized empty-state string.
 */
private fun pickText(
    texts: List<SpeciesText>,
    locale: Locale,
    kind: String,
): PickedText {
    val localized =
        texts
            .firstOrNull { it.locale == locale.code && it.kind == kind }
            ?.text
            ?.let(::cleanSpeciesText)
    if (!localized.isNullOrBlank()) return PickedText(localized, locale)
    val english =
        texts
            .firstOrNull { it.locale == Locale.EN.code && it.kind == kind }
            ?.text
            ?.let(::cleanSpeciesText)
    return if (english != null) PickedText(english, Locale.EN) else PickedText(localized, locale)
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
