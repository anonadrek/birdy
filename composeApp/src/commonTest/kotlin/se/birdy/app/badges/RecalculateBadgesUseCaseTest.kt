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
import se.birdy.domain.observation.ObservationSource
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

    // ===== T14: 5 new premium rules =====

    @Test
    fun `observed_before_hour — counts only obs whose local hour is lt threshold`() {
        val sthlm = TimeZone.of("Europe/Stockholm")
        val obs =
            listOf(
                obsAt("Q1", Instant.parse("2026-05-22T05:30:00Z")), // 07:30 CEST — skip
                obsAt("Q1", Instant.parse("2026-05-22T05:00:00Z")), // 07:00 CEST — skip
                obsAt("Q2", Instant.parse("2026-05-22T03:30:00Z")), // 05:30 CEST — qualifies
            )
        val rule = BadgeRule.ObservedBeforeHour(hour = 6, target = 1)
        val uc = RecalculateBadgesUseCase(zone = sthlm, clock = clock)
        val unlocks = uc.newUnlocks(obs, emptyMap(), catalogOf(badge("dawn", rule)), emptySet())
        assertEquals(listOf("dawn"), unlocks.map { it.badgeId })
    }

    @Test
    fun `observed_before_hour — target not met returns no unlock`() {
        val sthlm = TimeZone.of("Europe/Stockholm")
        val obs = listOf(obsAt("Q1", Instant.parse("2026-05-22T05:30:00Z"))) // 07:30 CEST
        val rule = BadgeRule.ObservedBeforeHour(hour = 6, target = 1)
        val uc = RecalculateBadgesUseCase(zone = sthlm, clock = clock)
        assertEquals(emptyList(), uc.newUnlocks(obs, emptyMap(), catalogOf(badge("dawn", rule)), emptySet()))
    }

    @Test
    fun `audio_observation_count — only Audio-source obs count`() {
        val obs =
            listOf(
                obs("Q1", day = 1).copy(sourceType = ObservationSource.Photo),
                obs("Q1", day = 2).copy(sourceType = ObservationSource.Audio),
                obs("Q2", day = 3).copy(sourceType = ObservationSource.Audio),
            )
        val rule = BadgeRule.AudioObservationCount(target = 2)
        val unlocks = recalc.newUnlocks(obs, emptyMap(), catalogOf(badge("audio2", rule)), emptySet())
        assertEquals(listOf("audio2"), unlocks.map { it.badgeId })
    }

    @Test
    fun `audio_observation_count — target not met`() {
        val obs =
            listOf(
                obs("Q1", day = 1).copy(sourceType = ObservationSource.Audio),
                obs("Q2", day = 2).copy(sourceType = ObservationSource.Photo),
            )
        val rule = BadgeRule.AudioObservationCount(target = 2)
        assertEquals(emptyList(), recalc.newUnlocks(obs, emptyMap(), catalogOf(badge("audio2", rule)), emptySet()))
    }

    @Test
    fun `species_across_seasons — same qid in 4 distinct seasons unlocks`() {
        val obs =
            listOf(
                obs("Q1", 2026, 4, 1), // spring
                obs("Q1", 2026, 7, 1), // summer
                obs("Q1", 2026, 10, 1), // autumn
                obs("Q1", 2026, 12, 15), // winter
            )
        val rule = BadgeRule.SpeciesAcrossSeasons(seasons = 4, target = 1)
        val unlocks = recalc.newUnlocks(obs, emptyMap(), catalogOf(badge("all4s", rule)), emptySet())
        assertEquals(listOf("all4s"), unlocks.map { it.badgeId })
    }

    @Test
    fun `species_across_seasons — different species per season does not count`() {
        val obs =
            listOf(
                obs("Q1", 2026, 4, 1), // spring
                obs("Q2", 2026, 7, 1), // summer
                obs("Q3", 2026, 10, 1), // autumn
                obs("Q4", 2026, 12, 15), // winter
            )
        val rule = BadgeRule.SpeciesAcrossSeasons(seasons = 4, target = 1)
        assertEquals(emptyList(), recalc.newUnlocks(obs, emptyMap(), catalogOf(badge("all4s", rule)), emptySet()))
    }

    @Test
    fun `species_across_seasons — null speciesId ignored`() {
        val obs =
            listOf(
                obs("Q1", 2026, 4, 1).copy(speciesId = null),
                obs("Q1", 2026, 7, 1).copy(speciesId = null),
            )
        val rule = BadgeRule.SpeciesAcrossSeasons(seasons = 2, target = 1)
        assertEquals(emptyList(), recalc.newUnlocks(obs, emptyMap(), catalogOf(badge("any2s", rule)), emptySet()))
    }

    @Test
    fun `observations_with_note — counts obs whose note length is gte minLength`() {
        val obs =
            listOf(
                obs("Q1", day = 1).copy(note = "a".repeat(5)),
                obs("Q2", day = 2).copy(note = "a".repeat(20)),
                obs("Q3", day = 3).copy(note = "a".repeat(50)),
            )
        val rule = BadgeRule.ObservationsWithNote(minLength = 20, target = 2)
        val unlocks = recalc.newUnlocks(obs, emptyMap(), catalogOf(badge("noter2", rule)), emptySet())
        assertEquals(listOf("noter2"), unlocks.map { it.badgeId })
    }

    @Test
    fun `observations_with_note — empty notes never qualify`() {
        val obs = listOf(obs("Q1", day = 1), obs("Q2", day = 2))
        val rule = BadgeRule.ObservationsWithNote(minLength = 1, target = 1)
        assertEquals(emptyList(), recalc.newUnlocks(obs, emptyMap(), catalogOf(badge("noter1", rule)), emptySet()))
    }

    @Test
    fun `observed_in_all_seasons — all 4 seasons unlocks`() {
        val obs =
            listOf(
                obs("Q1", 2026, 1, 15), // winter
                obs("Q2", 2026, 4, 1), // spring
                obs("Q3", 2026, 7, 1), // summer
                obs("Q4", 2026, 10, 1), // autumn
            )
        val rule = BadgeRule.ObservedInAllSeasons(target = 1)
        val unlocks = recalc.newUnlocks(obs, emptyMap(), catalogOf(badge("seasonal", rule)), emptySet())
        assertEquals(listOf("seasonal"), unlocks.map { it.badgeId })
    }

    @Test
    fun `observed_in_all_seasons — only 3 seasons does not unlock`() {
        val obs =
            listOf(
                obs("Q1", 2026, 1, 15), // winter
                obs("Q2", 2026, 4, 1), // spring
                obs("Q3", 2026, 7, 1), // summer
            )
        val rule = BadgeRule.ObservedInAllSeasons(target = 1)
        assertEquals(emptyList(), recalc.newUnlocks(obs, emptyMap(), catalogOf(badge("seasonal", rule)), emptySet()))
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

    private fun obsAt(
        speciesId: String,
        instant: Instant,
    ): Observation =
        Observation(
            id = "obs-$speciesId-${instant.toEpochMilliseconds()}",
            speciesId = speciesId,
            capturedAt = instant,
            savedAt = instant,
            photoPath = "/tmp/photo.jpg",
            note = "",
            confidence = 0.9f,
            latitude = null,
            longitude = null,
            locationLabel = null,
        )

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
