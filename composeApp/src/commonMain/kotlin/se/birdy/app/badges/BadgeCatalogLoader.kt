package se.birdy.app.badges

import birdy_bird_scanner.composeapp.generated.resources.Res
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlException
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.ExperimentalResourceApi
import se.birdy.domain.badge.Badge
import se.birdy.domain.badge.BadgeAbundance
import se.birdy.domain.badge.BadgeCatalog
import se.birdy.domain.badge.BadgeCategory
import se.birdy.domain.badge.BadgeRule
import se.birdy.domain.badge.BadgeSeason

class BadgeCatalogException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

object BadgeCatalogLoader {
    fun parse(yamlText: String): BadgeCatalog = decode(yamlText, premium = false)

    fun parsePremium(yamlText: String): BadgeCatalog = decode(yamlText, premium = true)

    private fun decode(
        yamlText: String,
        premium: Boolean,
    ): BadgeCatalog {
        val raw =
            try {
                Yaml.default.decodeFromString(RawCatalog.serializer(), yamlText)
            } catch (e: YamlException) {
                throw BadgeCatalogException("YAML parse error: ${e.message}", e)
            } catch (e: kotlinx.serialization.SerializationException) {
                throw BadgeCatalogException("Schema mismatch: ${e.message}", e)
            }

        val seenIds = mutableSetOf<String>()
        val badges =
            raw.badges.map { rb ->
                if (!seenIds.add(rb.id)) {
                    throw BadgeCatalogException("Duplicate badge id: ${rb.id}")
                }
                Badge(
                    id = rb.id,
                    category = parseCategory(rb.category),
                    rule = parseRule(rb.id, rb.rule),
                    isPremium = premium,
                )
            }
        return BadgeCatalog(version = raw.version, badges = badges)
    }

    @OptIn(ExperimentalResourceApi::class)
    suspend fun loadFromResources(): BadgeCatalog {
        val regular = parse(Res.readBytes("files/badges.yaml").decodeToString())
        val premium = parsePremium(Res.readBytes("files/premium_badges.yaml").decodeToString())
        val mergedIds = mutableSetOf<String>()
        (regular.badges + premium.badges).forEach { b ->
            if (!mergedIds.add(b.id)) {
                throw BadgeCatalogException("Duplicate badge id across regular+premium: ${b.id}")
            }
        }
        return BadgeCatalog(
            version = regular.version * 100 + premium.version,
            badges = regular.badges + premium.badges,
        )
    }

    private fun parseCategory(raw: String): BadgeCategory =
        when (raw) {
            "progression" -> BadgeCategory.PROGRESSION
            "streak_weekly" -> BadgeCategory.STREAK_WEEKLY
            "streak_monthly" -> BadgeCategory.STREAK_MONTHLY
            "season" -> BadgeCategory.SEASON
            "family" -> BadgeCategory.FAMILY
            "rare" -> BadgeCategory.REDLISTED
            else -> throw BadgeCatalogException("Unknown category: $raw")
        }

    private fun parseRule(
        badgeId: String,
        raw: RawRule,
    ): BadgeRule =
        when (raw.type) {
            "count_unique_species" -> BadgeRule.CountUniqueSpecies(raw.target ?: missing(badgeId, "target"))
            "weekly_streak" -> BadgeRule.WeeklyStreak(raw.target ?: missing(badgeId, "target"))
            "monthly_streak" -> BadgeRule.MonthlyStreak(raw.target ?: missing(badgeId, "target"))
            "observed_in_season" ->
                BadgeRule.ObservedInSeason(
                    season = parseSeason(raw.season ?: missing(badgeId, "season")),
                    target = raw.target ?: missing(badgeId, "target"),
                )
            "observed_in_family" ->
                BadgeRule.ObservedInFamily(
                    family = raw.family ?: missing(badgeId, "family"),
                    target = raw.target ?: missing(badgeId, "target"),
                )
            "observed_with_abundance" ->
                BadgeRule.ObservedWithAbundance(
                    abundance = parseAbundance(raw.abundance ?: missing(badgeId, "abundance")),
                    target = raw.target ?: missing(badgeId, "target"),
                )
            "observed_before_hour" ->
                BadgeRule.ObservedBeforeHour(
                    hour = raw.hour ?: missing(badgeId, "hour"),
                    target = raw.target ?: missing(badgeId, "target"),
                )
            "observed_in_hour_range" ->
                BadgeRule.ObservedInHourRange(
                    startHour = raw.startHour ?: missing(badgeId, "startHour"),
                    endHourExclusive = raw.endHourExclusive ?: missing(badgeId, "endHourExclusive"),
                    target = raw.target ?: missing(badgeId, "target"),
                )
            "sunday_streak" ->
                BadgeRule.SundayStreak(target = raw.target ?: missing(badgeId, "target"))
            "daily_bird_matches" ->
                BadgeRule.DailyBirdMatches(target = raw.target ?: missing(badgeId, "target"))
            "species_across_seasons" ->
                BadgeRule.SpeciesAcrossSeasons(
                    seasons = raw.seasons ?: missing(badgeId, "seasons"),
                    target = raw.target ?: missing(badgeId, "target"),
                )
            "audio_observation_count" ->
                BadgeRule.AudioObservationCount(
                    target = raw.target ?: missing(badgeId, "target"),
                )
            "observations_with_note" ->
                BadgeRule.ObservationsWithNote(
                    minLength = raw.minLength ?: missing(badgeId, "minLength"),
                    target = raw.target ?: missing(badgeId, "target"),
                )
            "observed_in_all_seasons" ->
                BadgeRule.ObservedInAllSeasons(
                    target = raw.target ?: missing(badgeId, "target"),
                )
            "manual" -> BadgeRule.Manual
            else -> throw BadgeCatalogException("Unknown rule type for $badgeId: ${raw.type}")
        }

    private fun parseSeason(raw: String): BadgeSeason =
        when (raw.lowercase()) {
            "winter" -> BadgeSeason.WINTER
            "spring" -> BadgeSeason.SPRING
            "summer" -> BadgeSeason.SUMMER
            "autumn" -> BadgeSeason.AUTUMN
            else -> throw BadgeCatalogException("Unknown season: $raw")
        }

    private fun parseAbundance(raw: String): BadgeAbundance =
        when (raw.lowercase()) {
            "allmän" -> BadgeAbundance.ALLMÄN
            "mindre_allmän" -> BadgeAbundance.MINDRE_ALLMÄN
            "ovanlig" -> BadgeAbundance.OVANLIG
            "sällsynt" -> BadgeAbundance.SÄLLSYNT
            else -> throw BadgeCatalogException("Unknown abundance: $raw")
        }

    private fun missing(
        badgeId: String,
        field: String,
    ): Nothing = throw BadgeCatalogException("Badge $badgeId missing required field: $field")

    @Serializable
    private data class RawCatalog(
        val version: Int,
        val badges: List<RawBadge>,
    )

    @Serializable
    private data class RawBadge(
        val id: String,
        val category: String,
        val rule: RawRule,
    )

    @Serializable
    private data class RawRule(
        val type: String,
        val target: Int? = null,
        val season: String? = null,
        val family: String? = null,
        val abundance: String? = null,
        val hour: Int? = null,
        val seasons: Int? = null,
        val minLength: Int? = null,
        val startHour: Int? = null,
        val endHourExclusive: Int? = null,
    )
}
