package se.birdy.app.badges

import se.birdy.domain.badge.BadgeAbundance
import se.birdy.domain.badge.BadgeCategory
import se.birdy.domain.badge.BadgeRule
import se.birdy.domain.badge.BadgeSeason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BadgeCatalogLoaderTest {
    @Test
    fun `parse valid yaml returns 25 badges with version 1`() {
        val yaml = validYaml()
        val catalog = BadgeCatalogLoader.parse(yaml)
        assertEquals(1, catalog.version)
        assertEquals(25, catalog.badges.size)
        val novice = catalog.findById("novice")!!
        assertEquals(BadgeCategory.PROGRESSION, novice.category)
        assertEquals(BadgeRule.CountUniqueSpecies(5), novice.rule)
    }

    @Test
    fun `parse maps all 6 rule types correctly`() {
        val yaml = validYaml()
        val catalog = BadgeCatalogLoader.parse(yaml)

        assertEquals(BadgeRule.CountUniqueSpecies(5), catalog.findById("novice")!!.rule)
        assertEquals(BadgeRule.WeeklyStreak(4), catalog.findById("weekly_streak_4")!!.rule)
        assertEquals(BadgeRule.MonthlyStreak(3), catalog.findById("monthly_streak_3")!!.rule)
        assertEquals(
            BadgeRule.ObservedInSeason(BadgeSeason.WINTER, 10),
            catalog.findById("season_winter")!!.rule,
        )
        assertEquals(
            BadgeRule.ObservedInFamily("anatidae", 1),
            catalog.findById("family_anatidae")!!.rule,
        )
        assertEquals(
            BadgeRule.ObservedWithAbundance(BadgeAbundance.SÄLLSYNT, 1),
            catalog.findById("rare_first")!!.rule,
        )
    }

    @Test
    fun `parse fails on unknown rule type`() {
        val yaml =
            """
            version: 1
            badges:
              - id: bad
                category: progression
                rule: { type: count_pizzas, target: 5 }
            """.trimIndent()
        assertFailsWith<BadgeCatalogException> { BadgeCatalogLoader.parse(yaml) }
    }

    @Test
    fun `parse fails on unknown category`() {
        val yaml =
            """
            version: 1
            badges:
              - id: bad
                category: bogus
                rule: { type: count_unique_species, target: 5 }
            """.trimIndent()
        assertFailsWith<BadgeCatalogException> { BadgeCatalogLoader.parse(yaml) }
    }

    @Test
    fun `parse fails on missing required field`() {
        val yaml =
            """
            version: 1
            badges:
              - id: bad
                category: progression
                # missing rule
            """.trimIndent()
        assertFailsWith<BadgeCatalogException> { BadgeCatalogLoader.parse(yaml) }
    }

    @Test
    fun `parse fails on duplicate id`() {
        val yaml =
            """
            version: 1
            badges:
              - id: dup
                category: progression
                rule: { type: count_unique_species, target: 5 }
              - id: dup
                category: progression
                rule: { type: count_unique_species, target: 25 }
            """.trimIndent()
        assertFailsWith<BadgeCatalogException> { BadgeCatalogLoader.parse(yaml) }
    }

    @Test
    fun `parse fails on syntax error`() {
        val yaml = "not: valid: yaml: ::"
        assertFailsWith<BadgeCatalogException> { BadgeCatalogLoader.parse(yaml) }
    }

    @Test
    fun `parsePremium parses v2 yaml with new rule types and sets isPremium=true`() {
        val yaml =
            """
            version: 2
            badges:
              - id: premium_dawn_chorus
                category: rare
                rule: { type: observed_before_hour, hour: 6, target: 5 }
              - id: premium_migration_mapper
                category: rare
                rule: { type: species_across_seasons, seasons: 4, target: 1 }
              - id: premium_song_scholar
                category: rare
                rule: { type: audio_observation_count, target: 5 }
              - id: premium_field_journalist
                category: rare
                rule: { type: observations_with_note, minLength: 30, target: 25 }
              - id: premium_seasonal_steward
                category: season
                rule: { type: observed_in_all_seasons, target: 1 }
              - id: premium_field_member
                category: rare
                rule: { type: manual, target: 1 }
            """.trimIndent()

        val catalog = BadgeCatalogLoader.parsePremium(yaml)

        assertEquals(2, catalog.version)
        assertEquals(6, catalog.badges.size)
        catalog.badges.forEach { assertTrue(it.isPremium, "${it.id} should be premium") }

        assertEquals(
            BadgeRule.ObservedBeforeHour(hour = 6, target = 5),
            catalog.findById("premium_dawn_chorus")!!.rule,
        )
        assertEquals(
            BadgeRule.SpeciesAcrossSeasons(seasons = 4, target = 1),
            catalog.findById("premium_migration_mapper")!!.rule,
        )
        assertEquals(
            BadgeRule.AudioObservationCount(target = 5),
            catalog.findById("premium_song_scholar")!!.rule,
        )
        assertEquals(
            BadgeRule.ObservationsWithNote(minLength = 30, target = 25),
            catalog.findById("premium_field_journalist")!!.rule,
        )
        assertEquals(
            BadgeRule.ObservedInAllSeasons(target = 1),
            catalog.findById("premium_seasonal_steward")!!.rule,
        )
        assertEquals(
            BadgeRule.Manual,
            catalog.findById("premium_field_member")!!.rule,
        )
    }

    @Test
    fun `parse without parsePremium leaves isPremium=false`() {
        val yaml = validYaml()
        val catalog = BadgeCatalogLoader.parse(yaml)
        catalog.badges.forEach { assertEquals(false, it.isPremium, "${it.id} should NOT be premium") }
    }

    @Test
    fun `parse observed_in_hour_range rule`() {
        val yaml =
            """
            version: 1
            badges:
              - id: early_pilgrim
                category: progression
                rule: { type: observed_in_hour_range, startHour: 4, endHourExclusive: 7, target: 5 }
            """.trimIndent()
        val catalog = BadgeCatalogLoader.parse(yaml)
        assertEquals(
            BadgeRule.ObservedInHourRange(startHour = 4, endHourExclusive = 7, target = 5),
            catalog.findById("early_pilgrim")!!.rule,
        )
    }

    @Test
    fun `parse sunday_streak rule`() {
        val yaml =
            """
            version: 1
            badges:
              - id: sunday_birder
                category: progression
                rule: { type: sunday_streak, target: 4 }
            """.trimIndent()
        val catalog = BadgeCatalogLoader.parse(yaml)
        assertEquals(BadgeRule.SundayStreak(target = 4), catalog.findById("sunday_birder")!!.rule)
    }

    @Test
    fun `parse daily_bird_matches rule`() {
        val yaml =
            """
            version: 1
            badges:
              - id: daily_bird_hunter
                category: progression
                rule: { type: daily_bird_matches, target: 10 }
            """.trimIndent()
        val catalog = BadgeCatalogLoader.parse(yaml)
        assertEquals(BadgeRule.DailyBirdMatches(target = 10), catalog.findById("daily_bird_hunter")!!.rule)
    }

    @Test
    fun `observed_in_hour_range without startHour throws BadgeCatalogException`() {
        val yaml =
            """
            version: 1
            badges:
              - id: bad
                category: progression
                rule: { type: observed_in_hour_range, endHourExclusive: 7, target: 1 }
            """.trimIndent()
        assertFailsWith<BadgeCatalogException> { BadgeCatalogLoader.parse(yaml) }
    }

    private fun validYaml(): String =
        """
        version: 1
        badges:
          - id: novice
            category: progression
            rule: { type: count_unique_species, target: 5 }
          - id: birder_bronze
            category: progression
            rule: { type: count_unique_species, target: 25 }
          - id: birder_silver
            category: progression
            rule: { type: count_unique_species, target: 100 }
          - id: weekly_streak_4
            category: streak_weekly
            rule: { type: weekly_streak, target: 4 }
          - id: weekly_streak_12
            category: streak_weekly
            rule: { type: weekly_streak, target: 12 }
          - id: weekly_streak_26
            category: streak_weekly
            rule: { type: weekly_streak, target: 26 }
          - id: weekly_streak_52
            category: streak_weekly
            rule: { type: weekly_streak, target: 52 }
          - id: monthly_streak_3
            category: streak_monthly
            rule: { type: monthly_streak, target: 3 }
          - id: monthly_streak_6
            category: streak_monthly
            rule: { type: monthly_streak, target: 6 }
          - id: monthly_streak_12
            category: streak_monthly
            rule: { type: monthly_streak, target: 12 }
          - id: season_winter
            category: season
            rule: { type: observed_in_season, season: winter, target: 10 }
          - id: season_spring
            category: season
            rule: { type: observed_in_season, season: spring, target: 5 }
          - id: season_summer
            category: season
            rule: { type: observed_in_season, season: summer, target: 5 }
          - id: season_autumn
            category: season
            rule: { type: observed_in_season, season: autumn, target: 5 }
          - id: family_anatidae
            category: family
            rule: { type: observed_in_family, family: anatidae, target: 1 }
          - id: family_paridae
            category: family
            rule: { type: observed_in_family, family: paridae, target: 1 }
          - id: family_accipitridae
            category: family
            rule: { type: observed_in_family, family: accipitridae, target: 1 }
          - id: family_corvidae
            category: family
            rule: { type: observed_in_family, family: corvidae, target: 1 }
          - id: family_fringillidae
            category: family
            rule: { type: observed_in_family, family: fringillidae, target: 1 }
          - id: family_turdidae
            category: family
            rule: { type: observed_in_family, family: turdidae, target: 1 }
          - id: family_sylviidae
            category: family
            rule: { type: observed_in_family, family: sylviidae, target: 1 }
          - id: family_picidae
            category: family
            rule: { type: observed_in_family, family: picidae, target: 1 }
          - id: rare_first
            category: rare
            rule: { type: observed_with_abundance, abundance: sällsynt, target: 1 }
          - id: rare_5
            category: rare
            rule: { type: observed_with_abundance, abundance: sällsynt, target: 5 }
          - id: rare_10
            category: rare
            rule: { type: observed_with_abundance, abundance: sällsynt, target: 10 }
        """.trimIndent()
}
