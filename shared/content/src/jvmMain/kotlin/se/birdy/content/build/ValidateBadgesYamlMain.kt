package se.birdy.content.build

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlList
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlScalar
import java.nio.file.Path

object ValidateBadgesYamlMain {
    private val validCategories =
        setOf("progression", "family", "breadth", "redlisted", "season", "audio", "streak_weekly", "streak_monthly")
    private val validRuleTypes =
        setOf(
            "count_unique_species",
            "weekly_streak",
            "monthly_streak",
            "observed_in_family",
            "observed_in_family_group",
            "count_distinct_families",
            "count_distinct_orders",
            "observed_red_listed",
            "observed_in_all_seasons",
            "species_across_seasons",
            "audio_observation_count",
            "daily_bird_matches",
        )

    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size >= 1) { "Usage: ValidateBadgesYamlMain <badges.yaml>" }
        val path = Path.of(args[0])
        val text = path.toFile().readText()
        val node =
            Yaml.default.parseToYamlNode(text) as? YamlMap
                ?: failExit("$path: root is not a YAML map")
        val version =
            node.scalarValue("version")?.toLongOrNull()
                ?: failExit("$path: missing 'version' (must be Long)")
        val badges = node.listValue("badges") ?: failExit("$path: missing 'badges' list")

        val ids = mutableSetOf<String>()
        val errors = mutableListOf<String>()

        badges.items.forEachIndexed { i, item ->
            val map =
                (item as? YamlMap) ?: run {
                    errors += "badges[$i]: not a map"
                    return@forEachIndexed
                }
            val id = map.scalarValue("id")
            val category = map.scalarValue("category")
            val rule = map.mapValue("rule")
            when {
                id.isNullOrBlank() -> errors += "badges[$i]: missing or blank id"
                !ids.add(id) -> errors += "badges[$i]: duplicate id '$id'"
                category == null || category !in validCategories ->
                    errors += "$id: invalid category '$category' (allowed: $validCategories)"
                rule == null -> errors += "$id: missing rule"
                else -> {
                    val type = rule.scalarValue("type")
                    if (type !in validRuleTypes) {
                        errors += "$id: invalid rule type '$type' (allowed: $validRuleTypes)"
                    } else {
                        val target = rule.scalarValue("target")?.toIntOrNull()
                        if (target == null || target <= 0) {
                            errors += "$id: missing or non-positive integer 'target' (got '${rule.scalarValue("target")}')"
                        }
                        when (type) {
                            "observed_in_family" -> {
                                val family = rule.scalarValue("family")
                                if (family.isNullOrBlank()) {
                                    errors += "$id: missing or blank family"
                                }
                            }
                            "observed_in_family_group" -> {
                                val families = rule.listValue("families")
                                if (families == null || families.items.isEmpty()) {
                                    errors += "$id: observed_in_family_group requires a non-empty families list"
                                }
                            }
                        }
                    }
                }
            }
        }

        if (errors.isNotEmpty()) {
            System.err.println("validateBadgesYaml: ${errors.size} errors:")
            errors.forEach { System.err.println("  $it") }
            kotlin.system.exitProcess(1)
        }
        println("validateBadgesYaml: version=$version, ${badges.items.size} badges, all valid.")
    }

    private fun failExit(msg: String): Nothing {
        System.err.println(msg)
        kotlin.system.exitProcess(1)
    }
}

internal fun YamlMap.scalarValue(key: String): String? =
    (entries.entries.firstOrNull { it.key.content == key }?.value as? YamlScalar)?.content

internal fun YamlMap.listValue(key: String): YamlList? = entries.entries.firstOrNull { it.key.content == key }?.value as? YamlList

internal fun YamlMap.mapValue(key: String): YamlMap? = entries.entries.firstOrNull { it.key.content == key }?.value as? YamlMap
