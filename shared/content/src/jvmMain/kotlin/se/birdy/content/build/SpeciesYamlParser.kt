package se.birdy.content.build

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.readText

class SpeciesYamlParser {
    private val yaml: Yaml =
        Yaml(
            configuration =
                YamlConfiguration(
                    strictMode = false,
                    encodeDefaults = false,
                ),
        )

    fun parse(path: Path): SpeciesYaml = yaml.decodeFromString(SpeciesYaml.serializer(), path.readText(Charsets.UTF_8))

    fun parseAll(speciesRoot: Path): List<Pair<Path, SpeciesYaml>> {
        if (!Files.isDirectory(speciesRoot)) return emptyList()
        return Files
            .walk(speciesRoot)
            .use { stream ->
                stream
                    .filter { Files.isRegularFile(it) && it.extension == "yaml" }
                    .map { it to parse(it) }
                    .toList()
            }
    }
}
