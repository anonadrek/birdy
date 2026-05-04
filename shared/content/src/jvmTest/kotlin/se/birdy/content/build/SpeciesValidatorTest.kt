package se.birdy.content.build

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path

class SpeciesValidatorTest {
    private val parser = SpeciesYamlParser()
    private val fixtureRoot: Path = Path.of("src/jvmTest/resources/fixtures/species")

    @Test
    fun `valid fixture passes`() {
        val items = parser.parseAll(fixtureRoot)
        val validator =
            SpeciesValidator(
                imageRoot = Path.of("src/jvmTest/resources/fixtures/images"),
                expectedCount = items.size,
                overrides = emptyMap(),
            )
        val errors = validator.validate(items)
        assertTrue(errors.isEmpty(), "Expected no errors, got: ${errors.joinToString("\n") { it.format() }}")
    }

    @Test
    fun `short description is rejected`() {
        val items =
            parser.parseAll(
                Path.of("src/jvmTest/resources/fixtures/invalid/short-desc"),
            )
        val validator =
            SpeciesValidator(
                imageRoot = Path.of("src/jvmTest/resources/fixtures/images"),
                expectedCount = 1,
                overrides = emptyMap(),
            )
        val errors = validator.validate(items)
        assertTrue(errors.any { it.rule == "description-too-short" })
    }

    @Test
    fun `id mismatch between filename and field is rejected`() {
        val items =
            parser.parseAll(
                Path.of("src/jvmTest/resources/fixtures/invalid/id-mismatch"),
            )
        val validator =
            SpeciesValidator(
                imageRoot = Path.of("src/jvmTest/resources/fixtures/images"),
                expectedCount = 1,
                overrides = emptyMap(),
            )
        val errors = validator.validate(items)
        assertTrue(errors.any { it.rule == "filename-id-mismatch" })
    }

    @Test
    fun `unknown region code is rejected`() {
        val items =
            parser.parseAll(
                Path.of("src/jvmTest/resources/fixtures/invalid/bad-region"),
            )
        val validator =
            SpeciesValidator(
                imageRoot = Path.of("src/jvmTest/resources/fixtures/images"),
                expectedCount = 1,
                overrides = emptyMap(),
            )
        val errors = validator.validate(items)
        assertTrue(errors.any { it.rule == "invalid-region" })
    }

    @Test
    fun `species count below expected is rejected`() {
        val items = parser.parseAll(fixtureRoot)
        val validator =
            SpeciesValidator(
                imageRoot = Path.of("src/jvmTest/resources/fixtures/images"),
                expectedCount = items.size + 10,
                overrides = emptyMap(),
            )
        val errors = validator.validate(items)
        assertTrue(errors.any { it.rule == "expected-count-mismatch" })
    }

    @Test
    fun `common species needing review still in auto state is rejected`() {
        val items = parser.parseAll(fixtureRoot)
        val mutated =
            items.map { (path, yaml) ->
                path to yaml.copy(review_status = "auto", abundance = "allmän")
            }
        val validator =
            SpeciesValidator(
                imageRoot = Path.of("src/jvmTest/resources/fixtures/images"),
                expectedCount = mutated.size,
                overrides = emptyMap(),
            )
        val errors = validator.validate(mutated)
        assertTrue(errors.any { it.rule == "common-needs-approval" })
    }
}
