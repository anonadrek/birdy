package se.birdy.content.build

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path

class SpeciesYamlParserTest {
    private val parser = SpeciesYamlParser()
    private val fixtureRoot: Path = Path.of("src/jvmTest/resources/fixtures/species")

    @Test
    fun `parses a single yaml file`() {
        val yaml = parser.parse(fixtureRoot.resolve("paridae/Q25485.yaml"))
        assertEquals("Q25485", yaml.id)
        assertEquals("Parus major", yaml.scientific_name)
        assertEquals("Paridae", yaml.taxonomy.family)
        assertEquals("Talgoxe", yaml.names.sv)
        assertEquals("allmän", yaml.abundance)
        assertEquals(1, yaml.image_refs.size)
        assertEquals("hero", yaml.image_refs[0].role)
    }

    @Test
    fun `parseAll walks all yaml files`() {
        val parsed = parser.parseAll(fixtureRoot)
        assertTrue(parsed.isNotEmpty())
        assertTrue(parsed.any { it.second.id == "Q25485" })
    }
}
