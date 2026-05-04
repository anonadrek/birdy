package se.birdy.content.build

import com.charleskorn.kaml.Yaml
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
)

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
                runCatching {
                    val text = overridesPath.toFile().readText()
                    val parsed = Yaml.default.decodeFromString(OverridesYaml.serializer(), text)
                    parsed.species.mapValues { (_, p) ->
                        OverrideEntry(
                            descriptionAcceptMissing = p.description_accept_missing.toSet(),
                            allowMissingImages = p.allow_missing_images,
                        )
                    }
                }.getOrDefault(emptyMap())
            } else {
                emptyMap()
            }

        val validator =
            SpeciesValidator(
                imageRoot = imagesDir,
                expectedCount = expectedCount,
                overrides = overrides,
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
