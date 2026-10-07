package se.birdy.content.build

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import se.birdy.content.Locale
import se.birdy.content.SpeciesFilter
import se.birdy.content.SpeciesId
import se.birdy.content.SqlDelightSpeciesRepository
import se.birdy.content.db.BirdyContent
import java.nio.file.Path

/**
 * Release 1.3.0 Task 7g item 7: hand corrections in the committed species YAML, guarded so a
 * pipeline `refresh` that brings the old values back fails here instead of reaching the app.
 */
class SpeciesContentCorrectionsTest {
    private val parser = SpeciesYamlParser()

    private fun species(path: String) = parser.parse(Path.of("species", path))

    // The YAML said NE (not evaluated) while its own text says endangered. BirdLife's 2023 IUCN
    // assessment (e.T22691900A226280431) moved Otis tarda from Vulnerable to Endangered; Wikidata's
    // P141 for Q171655 is "endangered" too.
    @Test
    fun `stortrapp is endangered on the global red list`() {
        assertEquals("EN", species("otididae/Q171655.yaml").iucn_status)
    }

    // The Swedish name was the scientific name. "Vinmajna" is BirdLife Sverige's official name
    // (list of February 2023), also the Swedish Wikipedia article's title.
    @Test
    fun `acridotheres leucocephalus is called vinmajna in swedish`() {
        assertEquals("Vinmajna", species("sturnidae/Q31874135.yaml").names.sv)
    }

    // The pipeline read IUCN categories by their English Wikidata label; in May 2026 those were
    // "endangered status" and "extinct species", so 12 endangered and 3 extinct species came out
    // as NE. Another 18 have no IUCN status on Wikidata at all; their IUCN Red List assessments
    // are now in tools/content-pipeline/species_list.yaml (source cited per species).
    @Test
    fun `endangered and extinct species are no longer written as not evaluated`() {
        assertEquals("EN", species("accipitridae/Q33504.yaml").iucn_status) // Smutsgam
        assertEquals("EN", species("threskiornithidae/Q245414.yaml").iucn_status) // Eremitibis
        assertEquals("EX", species("alcidae/Q189193.yaml").iucn_status) // Garfågel
        assertEquals("EX", species("haematopodidae/Q619728.yaml").iucn_status) // Kanariestrandskata
        assertEquals("EX", species("scolopacidae/Q76411.yaml").iucn_status) // Smalnäbbad spov
    }

    @Test
    fun `species without a status on wikidata carry the iucn red list one`() {
        assertEquals("LC", species("corvidae/Q25345384.yaml").iucn_status) // Kaja (Corvus monedula)
        assertEquals("LC", species("accipitridae/Q156250.yaml").iucn_status) // Röd glada
        assertEquals("VU", species("laridae/Q519583.yaml").iucn_status) // Rödnäbbad trut (Larus audouinii)
        assertEquals("NT", species("picidae/Q27074884.yaml").iucn_status) // Arabspett (Dendropicos dorae)
    }

    // What is left as NE: taxa IUCN has not assessed as species of their own (splits it still
    // lumps, such as Gråkråka, the chaffinches of the Atlantic islands and Siberian stonechat).
    @Test
    fun `only species iucn has not assessed on their own stay not evaluated`() {
        val notEvaluated =
            parser
                .parseAll(Path.of("species"))
                .map { it.second }
                .filter { it.iucn_status == "NE" }
                .map { it.id }
                .toSet()
        assertEquals(18, notEvaluated.size, notEvaluated.sorted().toString())
        assertTrue("Q25405" in notEvaluated) // Gråkråka
    }

    // 13 species had no Swedish name (or the scientific one): genus moves gave them new Wikidata
    // items without Swedish labels (Astur, Tachyspiza, Anarhynchus, Hydrobates, Gulosus,
    // Ortygornis), and for some the Swedish label was the scientific name. The names are now
    // BirdLife Sverige's official ones (version 2025), also kept in species_list.yaml.
    @Test
    fun `every species has a swedish name that is not its scientific name`() {
        val missing =
            parser
                .parseAll(Path.of("species"))
                .map { it.second }
                .filter { it.names.sv.isNullOrBlank() || it.names.sv.equals(it.scientific_name, ignoreCase = true) }
                .map { "${it.id} ${it.scientific_name}" }
        assertEquals(emptyList<String>(), missing)
    }

    @Test
    fun `renamed genera have their official swedish names`() {
        assertEquals("Duvhök", species("accipitridae/Q137474876.yaml").names.sv)
        assertEquals("Toppskarv", species("phalacrocoracidae/Q83020448.yaml").names.sv)
        assertEquals("Svartbent strandpipare", species("charadriidae/Q137156829.yaml").names.sv)
        assertEquals("Koreastormsvala", species("hydrobatidae/Q28122606.yaml").names.sv)
        assertEquals("Vitnäbbad islom", species("gaviidae/Q208328.yaml").names.sv)
    }

    // Vinmajna's Swedish text: "medlemi" (missing space) and "burmannusmajan" for the species it
    // was split from, whose official Swedish name is burmamajna. Its English texts are empty and
    // stay so (the app shows its own empty state; no new AI text in this task).
    @Test
    fun `vinmajna's swedish text has no typo and names burmamajnan`() {
        val text = species("sturnidae/Q31874135.yaml").description.getValue("sv").orEmpty()
        assertTrue("en medlem i familjen starar" in text, text)
        assertTrue("burmamajnan" in text, text)
        assertTrue("medlemi" !in text && "burmannusmajan" !in text, text)
    }

    private data class Renamed(
        val path: String,
        val official: String,
        val former: String,
    )

    // Release 1.3.0 Task 7m: the Swedish names that differed from BirdLife Sverige's official list
    // ("Officiella svenska namn på alla världens fågelarter", version 2025, file NL20.xlsx) now
    // follow it. The name Birdy used before stays as names.former_sv: a search term, and shown as
    // "Tidigare: ..." on the Swedish profile. All are also set in species_list.yaml (common_sv and
    // former_sv), so a pipeline refresh keeps them. Mapping and sources:
    // docs/superpowers/plans/1.3.0-tillagg/task07m-namn.md.
    private val renamed =
        listOf(
            Renamed("alaudidae/Q110812143.yaml", official = "Turkestanlärka", former = "Turkestandvärglärka"), // Alaudala heinei
            Renamed("anatidae/Q26452.yaml", official = "Skogsgås", former = "Sädgås"), // Anser fabalis
            Renamed("anatidae/Q673280.yaml", official = "Tundragås", former = "Tundrasädgås"), // Anser serrirostris
            Renamed("procellariidae/Q216850.yaml", official = "Diomedeslira", former = "Gulnäbbad lira"), // Calonectris diomedea
            Renamed("procellariidae/Q181323.yaml", official = "Större kapverdelira", former = "Kapverdelira"), // Calonectris edwardsii
            Renamed("cettiidae/Q650114.yaml", official = "Sumpcettia", former = "Cettisångare"), // Cettia cetti
            Renamed("alaudidae/Q1266617.yaml", official = "Skymningslärka", former = "Dupontlärka"), // Chersophilus duponti
            Renamed("odontophoridae/Q142651.yaml", official = "Virginiavaktel", former = "Vitstrupig vaktel"), // Colinus virginianus
            Renamed("sylviidae/Q110257505.yaml", official = "Tamarisksångare", former = "Östlig sammetshätta"), // Curruca mystacea
            Renamed("sylviidae/Q110257513.yaml", official = "Rosensångare", former = "Moltonisångare"), // Curruca subalpina
            Renamed("picidae/Q946681.yaml", official = "Gulbukig askspett", former = "Afrikansk gråspett"), // Dendropicos goertae
            Renamed("alaudidae/Q1092087.yaml", official = "Saharalärka", former = "Streckig ökenlärka"), // Eremalauda dunni
            Renamed("ploceidae/Q1060466.yaml", official = "Röd fody", former = "Rödfody"), // Foudia madagascariensis
            Renamed("fringillidae/Q847168.yaml", official = "Teneriffablåfink", former = "Blåfink"), // Fringilla teydea
            Renamed("accipitridae/Q55111925.yaml", official = "Fläckgam", former = "Rüppellgam"), // Gyps rueppelli
            Renamed("jacanidae/Q18861.yaml", official = "Fasanjassana", former = "Fasanjaçana"), // Hydrophasianus chirurgus
            Renamed("meropidae/Q1951359.yaml", official = "Grön biätare", former = "Blåkindad biätare"), // Merops persicus
            Renamed("alaudidae/Q1083050.yaml", official = "Drillärka", former = "Australisk lärka"), // Mirafra javanica
            Renamed("oceanitidae/Q845982.yaml", official = "Fregatthavslöpare", former = "Fregattstormsvala"), // Pelagodroma marina
            Renamed("phylloscopidae/Q3729103.yaml", official = "Berggransångare", former = "Kashmirgransångare"), // Phylloscopus sindianus
            Renamed("ploceidae/Q1306610.yaml", official = "Streckad vävare", former = "Streckig vävare"), // Ploceus manyar
            Renamed("psittaculidae/Q753746.yaml", official = "Storparakit", former = "Alexanderparakit"), // Psittacula eupatria
            Renamed("procellariidae/Q1261612.yaml", official = "Kapverdepetrell", former = "Kap Verdepetrell"), // Pterodroma feae
            Renamed("procellariidae/Q3410601.yaml", official = "Mindre kapverdelira", former = "Boydlira"), // Puffinus boydi
            Renamed("procellariidae/Q511566.yaml", official = "Medelhavslira", former = "Levantlira"), // Puffinus yelkouan
            Renamed("sittidae/Q851556.yaml", official = "Turknötväcka", former = "Krüpers nötväcka"), // Sitta krueperi
            Renamed("sittidae/Q928415.yaml", official = "Ravinnötväcka", former = "Östlig klippnötväcka"), // Sitta tephronota
            Renamed("strigidae/Q1272529.yaml", official = "Östlig klippuggla", former = "Klippuggla"), // Strix butleri
            // Zosterops abyssinicus
            Renamed("zosteropidae/Q3178456.yaml", official = "Abessinglasögonfågel", former = "Abessinsk glasögonfågel"),
        )

    @Test
    fun `swedish names follow birdlife sverige and keep the name birdy used before`() {
        val wrong =
            renamed.mapNotNull { r ->
                val names = species(r.path).names
                "${r.path}: ${names.sv} (former ${names.formerSv})"
                    .takeUnless { names.sv == r.official && names.formerSv == r.former }
            }
        assertEquals(emptyList<String>(), wrong)
    }

    @Test
    fun `only the renamed species carry a former name`() {
        val withFormer =
            parser
                .parseAll(Path.of("species"))
                .map { it.second }
                .filter { it.names.formerSv != null }
                .map { it.id }
                .toSet()
        assertEquals(renamed.map { it.path.substringAfter('/').removeSuffix(".yaml") }.toSet(), withFormer)
    }

    // The Swedish texts of the renamed species used the old name ("Sädgåsen är ..."). Current names
    // of species that contain a former name at a word start are masked first, so "Större
    // kapverdelira" or "Östlig klippuggla" does not count as a use of "Kapverdelira" or "Klippuggla".
    @Test
    fun `no swedish text of a renamed species uses its former name`() {
        val currentNames =
            parser
                .parseAll(Path.of("species"))
                .mapNotNull {
                    it.second.names.sv
                        ?.lowercase()
                }
        val uses =
            renamed.flatMap { r ->
                val former = r.former.lowercase()
                val atWordStart = Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(former))
                val masks =
                    currentNames
                        .filter { it != former && atWordStart.containsMatchIn(it) }
                        .sortedByDescending { it.length }
                val yaml = species(r.path)
                listOf("description" to yaml.description, "migration" to yaml.migration, "marginalia" to yaml.marginalia)
                    .mapNotNull { (kind, texts) ->
                        val text = masks.fold(texts["sv"].orEmpty().lowercase()) { t, name -> t.replace(name, " ") }
                        "${r.path} $kind.sv uses ${r.former}".takeIf { atWordStart.containsMatchIn(text) }
                    }
            }
        assertEquals(emptyList<String>(), uses)
    }

    // Diomedeslira (Calonectris diomedea, Scopoli's) had Swedish texts about Gulnäbbad lira (C.
    // borealis, Cory's, "förekommer främst i Atlanten"), written from the Wikipedia article of the
    // name it wrongly had. They are cleared; its English texts are empty too, so the app shows its
    // own empty-state texts. A pipeline refresh now reads the article "Diomedeslira"; update this
    // test when the new Swedish texts are in.
    @Test
    fun `diomedeslira has no swedish text about gulnäbbad lira`() {
        val diomedeslira = species("procellariidae/Q216850.yaml")
        assertEquals("", diomedeslira.description["sv"].orEmpty())
        assertEquals("", diomedeslira.migration["sv"].orEmpty())
    }

    // Calonectris diomedea (Scopoli's) shared "Gulnäbbad lira" with Calonectris borealis (Cory's);
    // BirdLife Sverige calls it diomedeslira.
    @Test
    fun `no two species share a swedish name`() {
        val duplicates =
            parser
                .parseAll(Path.of("species"))
                .map { it.second }
                .groupBy { it.names.sv?.lowercase() }
                .filterValues { it.size > 1 }
                .map { (name, list) -> "$name: ${list.map { it.id }}" }
        assertEquals(emptyList<String>(), duplicates)
    }

    @Test
    fun `searching a former name finds the renamed species first`(
        @TempDir tempDir: Path,
    ) = runTest {
        val db = tempDir.resolve("species.db")
        SpeciesDbBuilder().build(
            items = parser.parseAll(Path.of("species")),
            // No photos needed to search; a missing source folder copies none.
            sourceImageRoot = tempDir.resolve("no-images"),
            targetDb = db,
            targetImageRoot = tempDir.resolve("images"),
        )
        val driver = JdbcSqliteDriver("jdbc:sqlite:${db.toAbsolutePath()}")
        val repo = SqlDelightSpeciesRepository(BirdyContent(driver))
        val firstHits =
            listOf("sädgås", "Sädgås", "rödfody", "cettisångare", "Kap Verdepetrell", "Rüppellgam", "levantlira")
                .associateWith { query ->
                    repo
                        .search(query, Locale.SV, SpeciesFilter())
                        .first()
                        .firstOrNull()
                        ?.name
                }
        assertEquals(
            mapOf(
                "sädgås" to "Skogsgås",
                "Sädgås" to "Skogsgås",
                "rödfody" to "Röd fody",
                "cettisångare" to "Sumpcettia",
                "Kap Verdepetrell" to "Kapverdepetrell",
                "Rüppellgam" to "Fläckgam",
                "levantlira" to "Medelhavslira",
            ),
            firstHits,
        )
        assertEquals("Sädgås", repo.getById(SpeciesId("Q26452"), Locale.SV).first()?.formerName)
        assertEquals(null, repo.getById(SpeciesId("Q26452"), Locale.EN).first()?.formerName)
        driver.close()
    }
}
