package se.birdy.content.build

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlMap
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory

object ValidateBadgeStringsMain {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size >= 3) { "Usage: ValidateBadgeStringsMain <badges.yaml> <strings-sv.xml> <strings-en.xml>" }
        val yamlPath = Path.of(args[0])
        val svPath = Path.of(args[1])
        val enPath = Path.of(args[2])

        val ids = parseBadgeIds(yamlPath)
        val svKeys = parseStringsXmlKeys(svPath)
        val enKeys = parseStringsXmlKeys(enPath)

        val errors = mutableListOf<String>()
        ids.forEach { id ->
            listOf("badge_name_$id", "badge_desc_$id").forEach { key ->
                if (key !in svKeys) errors += "values/strings.xml (sv): missing '$key'"
                if (key !in enKeys) errors += "values-en/strings.xml (en): missing '$key'"
            }
        }
        if (errors.isNotEmpty()) {
            System.err.println("validateBadgeStrings: ${errors.size} missing keys:")
            errors.forEach { System.err.println("  $it") }
            kotlin.system.exitProcess(1)
        }
        val total = ids.size * 4
        println("validateBadgeStrings: ${ids.size} badges × 2 keys × 2 locales = $total keys verified.")
    }

    private fun parseBadgeIds(path: Path): List<String> {
        val node =
            Yaml.default.parseToYamlNode(path.toFile().readText()) as? YamlMap
                ?: return emptyList()
        val list = node.listValue("badges") ?: return emptyList()
        return list.items.mapNotNull { item ->
            (item as? YamlMap)?.scalarValue("id")
        }
    }

    private fun parseStringsXmlKeys(path: Path): Set<String> {
        val factory = DocumentBuilderFactory.newInstance()
        // Disable XXE — defensive even though strings.xml is trusted in-repo
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        val doc = factory.newDocumentBuilder().parse(path.toFile())
        val nodes = doc.getElementsByTagName("string")
        return (0 until nodes.length)
            .mapNotNull { i ->
                nodes
                    .item(i)
                    .attributes
                    .getNamedItem("name")
                    ?.nodeValue
            }.toSet()
    }
}
