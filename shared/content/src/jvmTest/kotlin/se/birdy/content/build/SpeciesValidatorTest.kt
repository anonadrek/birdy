package se.birdy.content.build

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
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

    private fun validateWithImage(transform: (ImageRefYaml) -> ImageRefYaml): List<ValidationError> {
        val items =
            parser.parseAll(fixtureRoot).map { (path, yaml) ->
                path to yaml.copy(image_refs = yaml.image_refs.map(transform))
            }
        return SpeciesValidator(
            imageRoot = Path.of("src/jvmTest/resources/fixtures/images"),
            expectedCount = items.size,
            overrides = emptyMap(),
        ).validate(items)
    }

    @Test
    fun `licence outside the allow-list is rejected`() {
        for (licence in listOf("CC BY-NC 2.0", "CC BY-ND 4.0", "GFDL", "CC BY-SA 2.5", "CC-BY-SA-4.0", "")) {
            val errors = validateWithImage { it.copy(license = licence) }
            assertTrue(errors.any { it.rule == "image-license-not-allowed" }, "licence '$licence' must be rejected")
        }
    }

    @Test
    fun `every allow-listed licence passes`() {
        for (licence in SpeciesValidator.ALLOWED_LICENSES) {
            val errors = validateWithImage { it.copy(license = licence) }
            assertTrue(errors.isEmpty(), "licence '$licence': ${errors.joinToString { it.format() }}")
        }
    }

    @Test
    fun `author that is still html is rejected`() {
        val errors = validateWithImage { it.copy(author = "<a href=\"//commons.wikimedia.org/wiki/User:X\">X</a>") }
        assertTrue(errors.any { it.rule == "image-author-html" })
    }

    @Test
    fun `attribution licence without a named author is rejected but public domain is not`() {
        val by = validateWithImage { it.copy(license = "CC BY 4.0", author = "Unknown") }
        assertTrue(by.any { it.rule == "image-author-missing" })
        val pd = validateWithImage { it.copy(license = "Public domain", author = "Unknown") }
        assertTrue(pd.none { it.rule == "image-author-missing" })
    }

    @Test
    fun `image file that no species references is reported when orphans are checked`(
        @TempDir tempDir: Path,
    ) {
        val items = parser.parseAll(fixtureRoot)
        val referenced =
            tempDir.resolve(
                items
                    .single()
                    .second.image_refs
                    .single()
                    .path,
            )
        Files.createDirectories(referenced.parent)
        Files.writeString(referenced, "photo")

        fun orphanErrors() =
            SpeciesValidator(tempDir, items.size, emptyMap(), checkOrphans = true)
                .validate(items)
                .filter { it.rule == "image-orphan" }

        assertTrue(orphanErrors().isEmpty(), "no orphans yet")
        Files.writeString(referenced.resolveSibling("secondary-2.webp"), "leftover")
        assertEquals(listOf("Q25485/secondary-2.webp"), orphanErrors().map { it.message.substringBefore(" ") })
    }

    private fun heroSizeErrors(
        width: Int,
        height: Int,
        overrides: Map<String, OverrideEntry> = emptyMap(),
    ): List<ValidationError> {
        val items =
            parser.parseAll(fixtureRoot).map { (path, yaml) ->
                path to yaml.copy(image_refs = yaml.image_refs.map { it.copy(width = width, height = height) })
            }
        return SpeciesValidator(Path.of("src/jvmTest/resources/fixtures/images"), items.size, overrides)
            .validate(items)
            .filter { it.rule == "hero-too-small" }
    }

    @Test
    fun `a small hero photo needs a documented per-species exception and never goes below 1000 px`() {
        assertTrue(heroSizeErrors(1024, 849).isNotEmpty(), "no exception: 2048 px rule")
        val exception = mapOf("Q25485" to OverrideEntry(heroMinSide = 1000))
        assertTrue(heroSizeErrors(1024, 849, exception).isEmpty(), "exception lets a 1024 px photo be the hero")
        assertTrue(heroSizeErrors(849, 999, exception).isNotEmpty(), "999 px is below the floor")
        val tooLow = mapOf("Q25485" to OverrideEntry(heroMinSide = 400))
        assertTrue(heroSizeErrors(640, 480, tooLow).isNotEmpty(), "an override can't go below 1000 px")
    }

    @Test
    fun `overrides file sets the per-species hero size exception`() {
        val parsed =
            parseOverrides(
                """
                species:
                  Q1143180:
                    hero_min_side: 1000  # only photo of the species >= 2048 px is obscured
                  Q207838:
                    description_accept_missing: [sv]
                """.trimIndent(),
            )
        assertEquals(1000, parsed.getValue("Q1143180").heroMinSide)
        assertEquals(null, parsed.getValue("Q207838").heroMinSide)
        assertEquals(setOf("sv"), parsed.getValue("Q207838").descriptionAcceptMissing)
    }

    @Test
    fun `finder and explorer files in the image folder are not orphans`(
        @TempDir tempDir: Path,
    ) {
        val items = parser.parseAll(fixtureRoot)
        val referenced =
            tempDir.resolve(
                items
                    .single()
                    .second.image_refs
                    .single()
                    .path,
            )
        Files.createDirectories(referenced.parent)
        Files.writeString(referenced, "photo")
        for (junk in listOf(".DS_Store", "Q25485/.DS_Store", "Q25485/._hero.jpg", "Q25485/Thumbs.db", "thumbs.db")) {
            Files.writeString(tempDir.resolve(junk), "x")
        }

        val errors = SpeciesValidator(tempDir, items.size, emptyMap(), checkOrphans = true).validate(items)
        assertTrue(errors.none { it.rule == "image-orphan" }, errors.joinToString { it.format() })
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
