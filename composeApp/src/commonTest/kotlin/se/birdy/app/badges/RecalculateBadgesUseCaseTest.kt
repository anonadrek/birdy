package se.birdy.app.badges

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import se.birdy.app.testing.FakeClock
import se.birdy.content.SpeciesId
import se.birdy.content.model.Species
import se.birdy.content.model.SpeciesTaxonomy
import se.birdy.domain.badge.Badge
import se.birdy.domain.badge.BadgeAbundance
import se.birdy.domain.badge.BadgeCatalog
import se.birdy.domain.badge.BadgeCategory
import se.birdy.domain.badge.BadgeRule
import se.birdy.domain.badge.BadgeSeason
import se.birdy.domain.badge.BadgeUnlock
import se.birdy.domain.observation.Observation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import se.birdy.content.Abundance as ContentAbundance

class RecalculateBadgesUseCaseTest {
    private val utc = TimeZone.UTC
    private val fixedNow = Instant.fromEpochMilliseconds(1_800_000_000_000)
    private val clock = FakeClock(fixedNow)
    private val recalc = RecalculateBadgesUseCase(zone = utc, clock = clock)

    @Test
    fun `count_unique_species — 5 unique observations unlocks novice`() {
        val obs = (1..5).map { obs(speciesId = "Q$it", day = it) }
        val catalog = catalogOf(badge("novice", BadgeRule.CountUniqueSpecies(5)))
        val newUnlocks = recalc.newUnlocks(obs, emptyMap(), catalog, emptySet())
        assertEquals(1, newUnlocks.size)
        assertEquals("novice", newUnlocks[0].badgeId)
        assertEquals(fixedNow, newUnlocks[0].unlockedAt)
    }

    @Test
    fun `count_unique_species — 4 unique does not unlock 5-target`() {
        val obs = (1..4).map { obs(speciesId = "Q$it", day = it) }
        val catalog = catalogOf(badge("novice", BadgeRule.CountUniqueSpecies(5)))
        assertEquals(emptyList(), recalc.newUnlocks(obs, emptyMap(), catalog, emptySet()))
    }

    @Test
    fun `count_unique_species — duplicates do not double-count`() {
        val obs =
            listOf(
                obs(speciesId = "Q1", day = 1),
                obs(speciesId = "Q1", day = 2),
                obs(speciesId = "Q1", day = 3),
            )
        val catalog = catalogOf(badge("novice", BadgeRule.CountUniqueSpecies(2)))
        assertEquals(emptyList(), recalc.newUnlocks(obs, emptyMap(), catalog, emptySet()))
    }

    @Test
    fun `existing unlocks are excluded`() {
        val obs = (1..5).map { obs(speciesId = "Q$it", day = it) }
        val catalog = catalogOf(badge("novice", BadgeRule.CountUniqueSpecies(5)))
        val existing = setOf("novice")
        assertEquals(emptyList(), recalc.newUnlocks(obs, emptyMap(), catalog, existing))
    }

    @Test
    fun `weekly_streak — 3 consecutive weeks unlocks streak_3`() {
        val obs =
            listOf(
                obs("Q1", 2026, 5, 4),
                obs("Q1", 2026, 5, 11),
                obs("Q1", 2026, 5, 18),
            )
        val catalog = catalogOf(badge("ws3", BadgeRule.WeeklyStreak(3)))
        assertEquals(listOf(BadgeUnlock("ws3", fixedNow)), recalc.newUnlocks(obs, emptyMap(), catalog, emptySet()))
    }

    @Test
    fun `weekly_streak — gap breaks streak`() {
        val obs =
            listOf(
                obs("Q1", 2026, 5, 4),
                obs("Q1", 2026, 5, 11),
                obs("Q1", 2026, 5, 25),
                obs("Q1", 2026, 6, 1),
            )
        val catalog = catalogOf(badge("ws3", BadgeRule.WeeklyStreak(3)))
        assertEquals(emptyList(), recalc.newUnlocks(obs, emptyMap(), catalog, emptySet()))
    }

    @Test
    fun `weekly_streak — cross-year v53 to v01 counts`() {
        val obs =
            listOf(
                obs("Q1", 2026, 12, 22),
                obs("Q1", 2026, 12, 29),
                obs("Q1", 2027, 1, 5),
            )
        val catalog = catalogOf(badge("ws3", BadgeRule.WeeklyStreak(3)))
        assertEquals(listOf("ws3"), recalc.newUnlocks(obs, emptyMap(), catalog, emptySet()).map { it.badgeId })
    }

    @Test
    fun `monthly_streak — 3 consecutive months unlocks`() {
        val obs =
            listOf(
                obs("Q1", 2026, 3, 15),
                obs("Q1", 2026, 4, 15),
                obs("Q1", 2026, 5, 15),
            )
        val catalog = catalogOf(badge("ms3", BadgeRule.MonthlyStreak(3)))
        assertEquals(listOf("ms3"), recalc.newUnlocks(obs, emptyMap(), catalog, emptySet()).map { it.badgeId })
    }

    @Test
    fun `observed_in_season — winter requires 10 in dec-feb`() {
        val obs = (1..10).map { obs("Q$it", 2026, 12, it) }
        val catalog = catalogOf(badge("winter10", BadgeRule.ObservedInSeason(BadgeSeason.WINTER, 10)))
        assertEquals(listOf("winter10"), recalc.newUnlocks(obs, emptyMap(), catalog, emptySet()).map { it.badgeId })
    }

    @Test
    fun `observed_in_season — only december counts as winter (meteorological)`() {
        val obs =
            listOf(
                obs("Q1", 2026, 11, 30),
                obs("Q1", 2026, 12, 1),
            )
        val catalog = catalogOf(badge("winter1", BadgeRule.ObservedInSeason(BadgeSeason.WINTER, 1)))
        assertEquals(listOf("winter1"), recalc.newUnlocks(obs, emptyMap(), catalog, emptySet()).map { it.badgeId })
    }

    @Test
    fun `observed_in_family — paridae match`() {
        val species =
            mapOf(
                SpeciesId("Q25612") to fakeSpecies("Q25612", family = "paridae"),
                SpeciesId("Q1") to fakeSpecies("Q1", family = "corvidae"),
            )
        val obs = listOf(obs("Q25612", 2026, 5, 4), obs("Q1", 2026, 5, 5))
        val catalog = catalogOf(badge("fam_paridae", BadgeRule.ObservedInFamily("paridae", 1)))
        assertEquals(listOf("fam_paridae"), recalc.newUnlocks(obs, species, catalog, emptySet()).map { it.badgeId })
    }

    @Test
    fun `observed_in_family — no match when family missing in species map`() {
        val obs = listOf(obs("Q-unknown", 2026, 5, 4))
        val catalog = catalogOf(badge("fam_paridae", BadgeRule.ObservedInFamily("paridae", 1)))
        assertEquals(emptyList(), recalc.newUnlocks(obs, emptyMap(), catalog, emptySet()))
    }

    @Test
    fun `observed_with_abundance — sällsynt 1 match`() {
        val species = mapOf(SpeciesId("Q1") to fakeSpecies("Q1", abundance = BadgeAbundance.SÄLLSYNT))
        val obs = listOf(obs("Q1", 2026, 5, 4))
        val catalog = catalogOf(badge("rare1", BadgeRule.ObservedWithAbundance(BadgeAbundance.SÄLLSYNT, 1)))
        assertEquals(listOf("rare1"), recalc.newUnlocks(obs, species, catalog, emptySet()).map { it.badgeId })
    }

    @Test
    fun `observed_with_abundance — non-matching abundance does not count`() {
        val species = mapOf(SpeciesId("Q1") to fakeSpecies("Q1", abundance = BadgeAbundance.OVANLIG))
        val obs = listOf(obs("Q1", 2026, 5, 4))
        val catalog = catalogOf(badge("rare1", BadgeRule.ObservedWithAbundance(BadgeAbundance.SÄLLSYNT, 1)))
        assertEquals(emptyList(), recalc.newUnlocks(obs, species, catalog, emptySet()))
    }

    @Test
    fun `multiple unlocks in one call returned in deterministic order`() {
        val obs = (1..5).map { obs("Q$it", 2026, 5, it) }
        val catalog =
            BadgeCatalog(
                version = 1,
                badges =
                    listOf(
                        badge("novice", BadgeRule.CountUniqueSpecies(5)),
                        badge("five_obs", BadgeRule.CountUniqueSpecies(3)),
                    ),
            )
        val unlocks = recalc.newUnlocks(obs, emptyMap(), catalog, emptySet())
        assertEquals(listOf("novice", "five_obs"), unlocks.map { it.badgeId })
    }

    @Test
    fun `currentValue returns count for count_unique`() {
        val obs = (1..3).map { obs("Q$it", 2026, 5, it) }
        assertEquals(3, recalc.currentValue(BadgeRule.CountUniqueSpecies(5), obs, emptyMap()))
    }

    @Test
    fun `currentValue caps at target`() {
        val obs = (1..10).map { obs("Q$it", 2026, 5, it) }
        assertEquals(5, recalc.currentValue(BadgeRule.CountUniqueSpecies(5), obs, emptyMap()))
    }

    @Test
    fun `empty observations return empty unlocks`() {
        val catalog = catalogOf(badge("novice", BadgeRule.CountUniqueSpecies(5)))
        assertTrue(recalc.newUnlocks(emptyList(), emptyMap(), catalog, emptySet()).isEmpty())
    }

    // ===== helpers =====

    private fun obs(
        speciesId: String,
        year: Int = 2026,
        month: Int = 5,
        day: Int = 1,
    ): Observation {
        val capturedAt = LocalDateTime(year, month, day, 12, 0).toInstant(utc)
        return Observation(
            id = "obs-$speciesId-$year-$month-$day",
            speciesId = speciesId,
            capturedAt = capturedAt,
            savedAt = capturedAt,
            photoPath = "/tmp/photo.jpg",
            note = "",
            confidence = 0.9f,
            latitude = null,
            longitude = null,
            locationLabel = null,
        )
    }

    private fun obs(
        speciesId: String,
        day: Int,
    ): Observation = obs(speciesId, 2026, 5, day)

    private fun badge(
        id: String,
        rule: BadgeRule,
    ): Badge = Badge(id, BadgeCategory.PROGRESSION, rule)

    private fun catalogOf(vararg badges: Badge): BadgeCatalog = BadgeCatalog(version = 1, badges = badges.toList())

    private fun fakeSpecies(
        qid: String,
        family: String = "unknown",
        abundance: BadgeAbundance = BadgeAbundance.OVANLIG,
    ): Species {
        val contentAbundance =
            when (abundance) {
                BadgeAbundance.ALLMÄN -> ContentAbundance.ALLMÄN
                BadgeAbundance.MINDRE_ALLMÄN -> ContentAbundance.MINDRE_ALLMÄN
                BadgeAbundance.OVANLIG -> ContentAbundance.OVANLIG
                BadgeAbundance.SÄLLSYNT -> ContentAbundance.SÄLLSYNT
            }
        return Species(
            id = SpeciesId(qid),
            scientificName = "Fakeus speciesius",
            taxonomy = SpeciesTaxonomy(family = family, familySv = null, genus = "Fakeus", iocOrder = "Fakeiformes"),
            name = qid,
            abundance = contentAbundance,
            iucnStatus = "LC",
            regions = emptyList(),
            season = emptyMap(),
            description = null,
            migration = null,
            images = emptyList(),
        )
    }
}
