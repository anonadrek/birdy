package se.birdy.content.build

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlList
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlScalar
import java.nio.file.Path

object ValidateBadgesYamlMain {
    private val validCategories = setOf("progression", "streak_weekly", "streak_monthly", "season", "family", "rare")
    private val validRuleTypes =
        setOf(
            "count_unique_species",
            "weekly_streak",
            "monthly_streak",
            "observed_in_season",
            "observed_in_family",
            "observed_with_abundance",
        )
    private val validSeasons = setOf("winter", "spring", "summer", "autumn")
    private val validAbundance = setOf("allmän", "mindre_allmän", "ovanlig", "sällsynt")

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
                        when (type) {
                            "observed_in_season" -> {
                                val season = rule.scalarValue("season")
                                if (season !in validSeasons) {
                                    errors += "$id: invalid season '$season' (allowed: $validSeasons)"
                                }
                            }
                            "observed_with_abundance" -> {
                                val abu = rule.scalarValue("abundance")
                                if (abu !in validAbundance) {
                                    errors += "$id: invalid abundance '$abu' (allowed: $validAbundance)"
                                }
                            }
                            "observed_in_family" -> {
                                val family = rule.scalarValue("family")
                                if (family.isNullOrBlank()) {
                                    errors += "$id: missing or blank family"
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
