package se.birdy.content.build

import org.junit.jupiter.api.Assertions.assertEquals
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
}
