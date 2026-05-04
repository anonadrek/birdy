package se.birdy.content.build

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import se.birdy.content.db.BirdyContent
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.copyTo

class SpeciesDbBuilder {
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
        db.speciesTaxonomyQueries.insert(
            species_id = yaml.id,
            family = yaml.taxonomy.family,
            family_sv = yaml.taxonomy.family_sv,
            genus = yaml.taxonomy.genus,
            ioc_order = yaml.taxonomy.ioc_order,
        )
        if (!yaml.names.sv.isNullOrBlank()) {
            db.speciesNameQueries.insert(yaml.id, "sv", yaml.names.sv!!)
        }
        db.speciesNameQueries.insert(yaml.id, "en", yaml.names.en)

        for ((lang, text) in yaml.description) {
            if (text.isNullOrBlank() || text == "[accept_missing]") continue
            db.speciesTextQueries.insert(yaml.id, lang, "description", text)
        }
        for ((lang, text) in yaml.migration) {
            if (text.isNullOrBlank()) continue
            db.speciesTextQueries.insert(yaml.id, lang, "migration", text)
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
}
