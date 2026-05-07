package se.birdy.content.build

object ValidateModelMappingMain {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 2) {
            "Usage: ValidateModelMappingMain <aiy_to_qid.json> <model_metadata.json>"
        }
        val mappingFile = java.io.File(args[0])
        val metadataFile = java.io.File(args[1])
        try {
            ValidateModelMapping.runFromFiles(mappingFile, metadataFile)
            println("validateModelMapping: OK")
        } catch (e: IllegalArgumentException) {
            System.err.println("validateModelMapping: ERROR — ${e.message}")
            kotlin.system.exitProcess(1)
        } catch (e: IllegalStateException) {
            System.err.println("validateModelMapping: ERROR — ${e.message}")
            kotlin.system.exitProcess(1)
        }
    }
}
