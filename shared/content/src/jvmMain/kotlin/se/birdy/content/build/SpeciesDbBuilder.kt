package se.birdy.content.build

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import se.birdy.content.db.BirdyContent
import se.birdy.content.search.normalizeSearch
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.copyTo

// Bumpa vid VARJE schema-ändring i Species*.sq → flippar application_id → tvingar DB-replace på uppgradering.
private const val SCHEMA_REV = 3

class SpeciesDbBuilder(
    private val familyGroups: FamilyGroups = FamilyGroups.loadDefault(),
) {
    fun build(
        items: List<Pair<Path, SpeciesYaml>>,
        sourceImageRoot: Path,
        targetDb: Path,
        targetImageRoot: Path,
    ) {
        Files.deleteIfExists(targetDb)
        targetDb.parent?.let { Files.createDirectories(it) }

        val driver =
            JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also {
                BirdyContent.Schema.create(it)
            }
        val db = BirdyContent(driver)
        db.transaction {
            for ((_, yaml) in items) {
                insertSpecies(db, yaml)
            }
        }

        driver.execute(null, "PRAGMA user_version = ${BirdyContent.Schema.version}", 0)
        driver.execute(null, "PRAGMA application_id = ${contentHash(items)}", 0)
        driver.execute(null, "VACUUM INTO '${targetDb.toAbsolutePath()}'", 0)
        driver.close()

        Files.createDirectories(targetImageRoot)
        for ((_, yaml) in items) {
            for (img in yaml.image_refs) {
                val source = sourceImageRoot.resolve(img.path)
                if (!Files.exists(source)) continue
                val target = targetImageRoot.resolve(img.path)
                Files.createDirectories(target.parent)
                source.copyTo(target, overwrite = true)
            }
        }
    }

    private fun insertSpecies(
        db: BirdyContent,
        yaml: SpeciesYaml,
    ) {
        db.speciesQueries.insert(
            id = yaml.id,
            scientific_name = yaml.scientific_name,
            abundance = yaml.abundance,
            iucn_status = yaml.iucn_status,
            generated_at = yaml.generated_at,
            review_status = yaml.review_status,
            wikipedia_sv_revision = yaml.sources.wikipedia_sv_revision,
            wikipedia_en_revision = yaml.sources.wikipedia_en_revision,
            claude_model = yaml.sources.claude_model,
        )
        val group = familyGroups.groupFor(yaml.taxonomy.family, yaml.taxonomy.ioc_order)
        if (!familyGroups.isExplicitlyMapped(yaml.taxonomy.family, yaml.taxonomy.ioc_order)) {
            System.err.println(
                "WARN: familjen '${yaml.taxonomy.family}' saknas i family_groups.yaml → '$group' (lägg till den)",
            )
        }
        db.speciesTaxonomyQueries.insert(
            species_id = yaml.id,
            family = yaml.taxonomy.family,
            family_sv = yaml.taxonomy.family_sv,
            genus = yaml.taxonomy.genus,
            ioc_order = yaml.taxonomy.ioc_order,
            group_id = group,
        )
        val sci = yaml.scientific_name
        val fam = yaml.taxonomy.family
        val famSv = yaml.taxonomy.family_sv ?: ""
        val genus = yaml.taxonomy.genus
        if (!yaml.names.sv.isNullOrBlank()) {
            val sv = yaml.names.sv!!
            db.speciesNameQueries.insert(yaml.id, "sv", sv, normalizeSearch("$sv $sci $fam $famSv $genus"))
        }
        val en = yaml.names.en
        db.speciesNameQueries.insert(yaml.id, "en", en, normalizeSearch("$en $sci $fam $famSv $genus"))

        for ((lang, text) in yaml.description) {
            if (text.isNullOrBlank() || text == "[accept_missing]") continue
            db.speciesTextQueries.insert(yaml.id, lang, "description", text)
        }
        for ((lang, text) in yaml.migration) {
            if (text.isNullOrBlank()) continue
            db.speciesTextQueries.insert(yaml.id, lang, "migration", text)
        }
        for ((lang, text) in yaml.marginalia) {
            if (text.isNullOrBlank()) continue
            db.speciesTextQueries.insert(yaml.id, lang, "marginalia", text)
        }
        for (region in yaml.regions) {
            db.speciesRegionQueries.insert(yaml.id, region)
        }
        for ((month, status) in yaml.season) {
            db.speciesSeasonQueries.insert(yaml.id, month, status)
        }
        for (img in yaml.image_refs) {
            db.speciesImageQueries.insert(
                species_id = yaml.id,
                role = img.role,
                path = img.path,
                width = img.width.toLong(),
                height = img.height.toLong(),
                license = img.license,
                author = img.author,
                source_url = img.source_url,
                commons_filename = img.commons_filename,
            )
        }
    }

    private fun contentHash(items: List<Pair<Path, SpeciesYaml>>): Int = contentFingerprint(items, SCHEMA_REV)

    /**
     * Stable content fingerprint stamped into the SQLite file header at byte
     * 68-71 (PRAGMA application_id). The Android repository compares this 4-byte
     * value between APK assets and the cached internal-storage copy on every
     * launch, and re-copies the bundled DB when they differ. This is what makes
     * new species data take effect on app upgrade without an app-data wipe.
     *
     * Hashed input: prefixed with `schema=<schemaRev>` so the fingerprint also
     * flips whenever the DB schema changes (bump SCHEMA_REV on any Species*.sq
     * change), even if all YAMLs are unchanged. After the prefix, each species'
     * id + generated_at is appended, sorted by id. Sorting makes the hash
     * insensitive to YAML file ordering; generated_at ensures any pipeline
     * `refresh` of an existing species also flips the fingerprint.
     */
    internal fun contentFingerprint(
        items: List<Pair<Path, SpeciesYaml>>,
        schemaRev: Int,
    ): Int {
        val signature =
            "schema=$schemaRev\n" +
                items
                    .map { (_, y) -> "${y.id}:${y.generated_at}" }
                    .sorted()
                    .joinToString("\n")
        val digest = MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
        return ((digest[0].toInt() and 0xFF) shl 24) or
            ((digest[1].toInt() and 0xFF) shl 16) or
            ((digest[2].toInt() and 0xFF) shl 8) or
            (digest[3].toInt() and 0xFF)
    }
}
