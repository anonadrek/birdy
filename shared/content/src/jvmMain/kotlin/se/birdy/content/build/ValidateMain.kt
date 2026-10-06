package se.birdy.content.build

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.nio.file.Path

@Serializable
private data class OverridesYaml(
    val species: Map<String, OverridesPatch> = emptyMap(),
)

@Serializable
private data class OverridesPatch(
    val description_accept_missing: List<String> = emptyList(),
    val allow_missing_images: Boolean = false,
    @SerialName("hero_min_side") val heroMinSide: Int? = null,
)

internal fun parseOverrides(text: String): Map<String, OverrideEntry> =
    Yaml.default.decodeFromString(OverridesYaml.serializer(), text).species.mapValues { (_, p) ->
        OverrideEntry(
            descriptionAcceptMissing = p.description_accept_missing.toSet(),
            allowMissingImages = p.allow_missing_images,
            heroMinSide = p.heroMinSide,
        )
    }

object ValidateMain {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size >= 3) { "Usage: ValidateMain <speciesDir> <imagesDir> <expectedCountFile> [overridesYaml]" }
        val speciesDir = Path.of(args[0])
        val imagesDir = Path.of(args[1])
        val expectedCount =
            Path
                .of(args[2])
                .toFile()
                .readText()
                .trim()
                .toInt()
        val overridesPath = if (args.size >= 4) Path.of(args[3]) else null

        val parser = SpeciesYamlParser()
        val items = parser.parseAll(speciesDir)

        val overrides: Map<String, OverrideEntry> =
            if (overridesPath != null && overridesPath.toFile().exists() && overridesPath.toFile().length() > 0) {
                runCatching { parseOverrides(overridesPath.toFile().readText()) }.getOrDefault(emptyMap())
            } else {
                emptyMap()
            }

        val validator =
            SpeciesValidator(
                imageRoot = imagesDir,
                expectedCount = expectedCount,
                overrides = overrides,
                checkOrphans = true,
            )
        val errors = validator.validate(items)

        if (errors.isEmpty()) {
            println("validateSpeciesData: ${items.size} species, all valid.")
            return
        }
        System.err.println("validateSpeciesData: ${errors.size} errors:")
        errors.forEach { System.err.println("  ${it.format()}") }
        kotlin.system.exitProcess(1)
    }
}
