package se.birdy.content.build

import java.io.File

object ValidateBirdNetMappingMain {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 2) {
            "Usage: ValidateBirdNetMappingMain <birdnet_lite_to_qid.json> <species_list.yaml>"
        }
        val mappingFile = File(args[0])
        val speciesFile = File(args[1])
        try {
            ValidateBirdNetMapping.runFromFiles(mappingFile, speciesFile)
            println("validateBirdNetMapping: OK")
        } catch (e: IllegalArgumentException) {
            System.err.println("validateBirdNetMapping: ERROR — ${e.message}")
            kotlin.system.exitProcess(1)
        } catch (e: IllegalStateException) {
            System.err.println("validateBirdNetMapping: ERROR — ${e.message}")
            kotlin.system.exitProcess(1)
        }
    }
}
