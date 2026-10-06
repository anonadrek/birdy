package se.birdy.content.build

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
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
}
