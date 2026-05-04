package se.birdy.content.build

import java.nio.file.Path
import kotlin.time.measureTime

object BuildMain {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 4) {
            "Usage: BuildMain <speciesDir> <sourceImagesDir> <targetDb> <targetImagesDir>"
        }
        val (speciesDir, sourceImages, targetDb, targetImages) = args.map(Path::of)
        val parser = SpeciesYamlParser()
        val items = parser.parseAll(speciesDir)

        val elapsed =
            measureTime {
                SpeciesDbBuilder().build(
                    items = items,
                    sourceImageRoot = sourceImages,
                    targetDb = targetDb,
                    targetImageRoot = targetImages,
                )
            }
        println("buildSpeciesDb: ${items.size} species → $targetDb in ${elapsed.inWholeMilliseconds} ms")
    }
}
