package se.birdy.domain.dailybird

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import se.birdy.content.Locale
import se.birdy.content.SqlDelightSpeciesRepository
import se.birdy.content.db.BirdyContent
import se.birdy.content.model.Species
import java.io.File
import kotlin.random.Random
import kotlin.test.Test

/**
 * Writes the website's golden file for its JavaScript port of [DailyBirdSelector] (website/src/lib/daily-bird.mjs,
 * website/tests/fixtures/daily-bird-golden.json): the real selector over the app's own species.db, every day's pick
 * from 2026-10-01 to 2027-12-31, the species list it picked from, and kotlin.random.Random and String.hashCode
 * vectors. A no-op unless BIRDY_GOLDEN_OUT names the file to write, so CI runs it without side effects:
 *
 *     BIRDY_GOLDEN_OUT=website/tests/fixtures/daily-bird-golden.json ./gradlew :shared:domain:jvmTest --tests "*DailyBirdGoldenGenerator*"
 *
 * BIRDY_GOLDEN_DB overrides the database (default: composeApp's bundled species.db, relative to this module). Run it
 * on the release branch whenever the selector or the species list changes, then `npm run app-species:snapshot` in
 * website/ (the website's release checklist, plan 2026-10-08 "Att komma ihåg").
 */
class DailyBirdGoldenGenerator {
    @Test
    fun generate() =
        runTest {
            val out = System.getenv("BIRDY_GOLDEN_OUT")?.takeIf { it.isNotBlank() } ?: return@runTest
            val dbPath = System.getenv("BIRDY_GOLDEN_DB")?.takeIf { it.isNotBlank() } ?: DEFAULT_DB
            require(File(dbPath).isFile) { "BIRDY_GOLDEN_DB: $dbPath finns inte" }
            val driver = JdbcSqliteDriver("jdbc:sqlite:$dbPath")
            try {
                val all = SqlDelightSpeciesRepository(BirdyContent(driver)).allByQid(Locale.SV)
                val selector = DailyBirdSelector(speciesProvider = { all })
                val text =
                    buildString {
                        append("{\n  \"_about\": ${json(ABOUT)},\n  \"species\": [\n")
                        all.values.sortedBy { it.id.raw }.forEachIndexed { i, species ->
                            if (i > 0) append(",\n")
                            append("    ${speciesJson(species)}")
                        }
                        append("\n  ],\n  \"days\": {\n")
                        var day = FIRST_DAY
                        while (day <= LAST_DAY) {
                            if (day != FIRST_DAY) append(",\n")
                            val pick = selector.selectFor(day)?.let { json(it.speciesId) } ?: "null"
                            append("    ${json(day.toString())}: $pick")
                            day = day.plus(1, DateTimeUnit.DAY)
                        }
                        append("\n  },\n  \"rng\": [\n")
                        append(rngVectors().joinToString(",\n") { "    $it" })
                        append("\n  ],\n  \"hash\": {")
                        append(HASHED.joinToString(",") { "${json(it)}:${it.hashCode()}" })
                        append("}\n}\n")
                    }
                File(out).writeText(text)
                println("DailyBirdGoldenGenerator: ${all.size} arter, $FIRST_DAY till $LAST_DAY, skrivet till $out")
            } finally {
                driver.close()
            }
        }

    /** One species as the website reads it (website/src/lib/daily-bird.mjs, the YAML's field names), compact JSON. */
    private fun speciesJson(species: Species): String {
        val regions = species.regions.joinToString(",") { json(it) }
        val season = MONTHS.mapNotNull { m -> species.season[m]?.let { "${json(m)}:${json(it)}" } }.joinToString(",")
        return "{\"id\":${json(species.id.raw)},\"abundance\":${json(species.abundance.code)}," +
            "\"iucn_status\":${json(species.iucnStatus)},\"regions\":[$regions],\"season\":{$season}}"
    }

    /** kotlin.random.Random(seed).nextInt(n) and nextInt() sequences the port must reproduce. */
    private fun rngVectors(): List<String> =
        SEEDS.flatMap { seed ->
            BOUNDS.map { n ->
                val rng = Random(seed)
                val draws = List(DRAWS) { rng.nextInt(n) }
                "{\"seed\":\"$seed\",\"n\":$n,\"draws\":[${draws.joinToString(",")}]}"
            } +
                run {
                    val rng = Random(seed)
                    "{\"seed\":\"$seed\",\"raw\":[${List(DRAWS) { rng.nextInt() }.joinToString(",")}]}"
                }
        }

    private fun json(s: String): String =
        buildString {
            append('"')
            for (c in s) {
                when (c) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\n' -> append("\\n")
                    else -> append(c)
                }
            }
            append('"')
        }

    private companion object {
        const val DEFAULT_DB = "../../composeApp/src/commonMain/composeResources/files/species.db"
        val FIRST_DAY = LocalDate(2026, 10, 1)
        val LAST_DAY = LocalDate(2027, 12, 31)
        val MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
        const val DRAWS = 4
        val SEEDS = listOf(0L, 1L, -1L, 42L, 2147483647L, -2147483648L, "2026-10-7-NORDIC".hashCode().toLong(), 123456789012345L)
        val BOUNDS = listOf(1, 2, 7, 16, 177, 180, 1000, 1 shl 30, 2147483647)
        val HASHED = listOf("2026-10-7-NORDIC", "2027-1-1-NORDIC", "", "å")
        const val ABOUT =
            "Golden file for src/lib/daily-bird.mjs, written by the app's own Kotlin DailyBirdSelector (release/1.3.0, the 1.3.0 rule " +
                "from 12c7526f) and kotlin.random.Random over the app's species.db through SqlDelightSpeciesRepository.allByQid, by " +
                "shared/domain/src/jvmTest/.../DailyBirdGoldenGenerator.kt (BIRDY_GOLDEN_OUT=<this file> ./gradlew " +
                ":shared:domain:jvmTest --tests \"*DailyBirdGoldenGenerator*\"). species: the selector's input, the shipped " +
                "app's list (npm run app-species:snapshot copies it to src/data/app-species-<version>.json). days: the pick for " +
                "every date. rng and hash: " +
                "kotlin.random.Random(seed).nextInt(n) and String.hashCode vectors. Regenerate on the release branch when the app's " +
                "selector or species list changes."
    }
}
