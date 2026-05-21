package se.birdy.content.build

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.system.exitProcess

object ValidateBirdNetMappingMain {
    @Serializable
    private data class SpeciesEntry(
        val wikidata_id: String,
        val scientific_name: String,
    )

    private val lenientYaml = Yaml(configuration = YamlConfiguration(strictMode = false))

    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 2) {
            "Usage: ValidateBirdNetMappingMain <birdnet_lite_to_qid.json> <species_list.yaml>"
        }
        val mappingFile = File(args[0])
        val speciesFile = File(args[1])

        val errors = mutableListOf<String>()

        val mappingJson = Json.parseToJsonElement(mappingFile.readText()).jsonObject
        val meta = mappingJson["_meta"]?.jsonObject
        val mapping = mappingJson["mapping"]?.jsonObject

        if (meta == null) errors += "_meta block missing in ${mappingFile.name}"
        if (mapping == null) errors += "mapping block missing"

        if (meta != null) {
            val coverage = meta["coverage_pct"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
            if (coverage < 70.0) errors += "coverage_pct $coverage < 70.0 threshold"
        }

        if (mapping != null) {
            val speciesList =
                lenientYaml.decodeFromString(
                    ListSerializer(SpeciesEntry.serializer()),
                    speciesFile.readText(),
                )
            val validQids = speciesList.map { it.wikidata_id }.toSet()

            val mapped = mapping.values.map { it.jsonPrimitive.content }
            val invalidQids = mapped.filterNot { it in validQids }
            if (invalidQids.isNotEmpty()) {
                errors += "Mapping references ${invalidQids.size} qids not in species_list: ${invalidQids.take(5)}"
            }
        }

        if (errors.isNotEmpty()) {
            System.err.println("BirdNET mapping validation FAILED:")
            errors.forEach { System.err.println("  x $it") }
            exitProcess(1)
        }

        val coverage =
            meta
                ?.get("coverage_pct")
                ?.jsonPrimitive
                ?.content
                ?.toDoubleOrNull() ?: 0.0
        println("validateBirdNetMapping: OK — ${mapping?.size ?: 0} entries, coverage $coverage%")
    }
}
