package se.birdy.app.recap

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import se.birdy.content.Abundance
import se.birdy.content.SpeciesId
import se.birdy.content.model.SpeciesSummary
import se.birdy.domain.badge.BadgeUnlock
import se.birdy.domain.observation.Observation
import se.birdy.domain.observation.ObservationSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

class WeeklyRecapBuilderTest {
    private val utc = TimeZone.UTC
    private val builder = WeeklyRecapBuilder(utc)
    private val now = Instant.parse("2026-05-27T10:00:00Z") // onsdag v22

    private fun daysAgo(d: Int) = now.minus(d.days)

    private fun obs(
        id: String,
        speciesId: String?,
        at: Instant,
        photoPath: String = "/p/$id.jpg",
        source: ObservationSource = ObservationSource.Photo,
        audioPath: String? = null,
    ) = Observation(
        id = id,
        speciesId = speciesId,
        capturedAt = at,
        savedAt = at,
        photoPath = photoPath,
        note = "",
        confidence = 0.9f,
        latitude = null,
        longitude = null,
        locationLabel = null,
        stampNumber = 0,
        audioPath = audioPath,
        sourceType = source,
    )

    @Test
    fun `quiet week has zero count and isQuiet`() {
        val s = builder.summarize(emptyList(), emptyList(), now)
        assertEquals(0, s.observationCount)
        assertTrue(s.isQuiet)
        assertFalse(s.streakAtRisk)
    }

    @Test
    fun `counts only current week observations`() {
        val list =
            listOf(
                obs("a", "Q1", daysAgo(1)), // denna vecka
                obs("b", "Q2", daysAgo(2)), // denna vecka
                obs("c", "Q3", daysAgo(9)), // förra veckan
            )
        val s = builder.summarize(list, emptyList(), now)
        assertEquals(2, s.observationCount)
        assertEquals(1, s.deltaVsLastWeek) // 2 - 1
        assertFalse(s.isQuiet)
    }

    @Test
    fun `new species counted only when first sighting falls this week`() {
        val list =
            listOf(
                obs("old", "Q1", daysAgo(30)), // Q1 sågs först förra månaden → ej ny
                obs("now1", "Q1", daysAgo(1)),
                obs("now2", "Q2", daysAgo(1)), // Q2 helt ny denna vecka
            )
        val s = builder.summarize(list, emptyList(), now)
        assertEquals(1, s.newSpeciesCount)
    }

    @Test
    fun `badges unlocked this week are listed`() {
        val unlocks =
            listOf(
                BadgeUnlock("premium_early_pilgrim", daysAgo(1)),
                BadgeUnlock("weekly_5", daysAgo(20)), // tidigare vecka
            )
        val s = builder.summarize(listOf(obs("a", "Q1", daysAgo(1))), unlocks, now)
        assertEquals(listOf("premium_early_pilgrim"), s.newBadgeIds)
    }

    @Test
    fun `streak at risk when quiet week but prior weeks active`() {
        val list =
            listOf(
                obs("w21", "Q1", daysAgo(8)), // förra veckan
                obs("w20", "Q1", daysAgo(15)), // veckan innan
            )
        val s = builder.summarize(list, emptyList(), now)
        assertEquals(0, s.observationCount)
        assertEquals(2, s.weeklyStreak)
        assertTrue(s.streakAtRisk)
    }

    // ── selectFinds helpers ─────────────────────────────────────────────────

    private fun species(
        qid: String,
        abundance: Abundance,
        hero: String? = "$qid/hero.webp",
    ): SpeciesSummary =
        SpeciesSummary(
            id = SpeciesId(qid),
            name = "Namn $qid",
            scientificName = "Sci $qid",
            abundance = abundance,
            heroImagePath = hero,
        )

    // ── selectFinds tests ───────────────────────────────────────────────────

    @Test
    fun `selectFinds returns only current week, newest first`() {
        val list =
            listOf(
                obs("b", "Q2", daysAgo(2)), // mån, denna vecka
                obs("a", "Q1", daysAgo(1)), // tis, denna vecka (nyare)
                obs("old", "Q3", daysAgo(9)), // tidigare vecka
            )
        val sp =
            mapOf(
                SpeciesId("Q1") to species("Q1", Abundance.ALLMÄN),
                SpeciesId("Q2") to species("Q2", Abundance.ALLMÄN),
                SpeciesId("Q3") to species("Q3", Abundance.ALLMÄN),
            )
        val finds = builder.selectFinds(list, sp, now)
        assertEquals(listOf("a", "b"), finds.map { it.observationId }) // nyaste först
    }

    @Test
    fun `selectFinds flags new species`() {
        val list =
            listOf(
                obs("old", "Q1", daysAgo(40)), // Q1 sedd tidigare → ej ny
                obs("c1", "Q1", daysAgo(1)),
                obs("c2", "Q2", daysAgo(2)), // Q2 ny denna vecka
            )
        val sp =
            mapOf(
                SpeciesId("Q1") to species("Q1", Abundance.ALLMÄN),
                SpeciesId("Q2") to species("Q2", Abundance.ALLMÄN),
            )
        val finds = builder.selectFinds(list, sp, now).associateBy { it.observationId }
        assertFalse(finds.getValue("c1").isNewSpecies)
        assertTrue(finds.getValue("c2").isNewSpecies)
    }

    @Test
    fun `selectFinds uses species heroImagePath for audio-only find`() {
        val list =
            listOf(
                obs("au", "Q2", daysAgo(1), photoPath = "", source = ObservationSource.Audio, audioPath = "/a/au.ogg"),
            )
        val sp = mapOf(SpeciesId("Q2") to species("Q2", Abundance.SÄLLSYNT, hero = "Q2/hero.webp"))
        val find = builder.selectFinds(list, sp, now).single()
        assertEquals("", find.photoPath)
        assertEquals("Q2/hero.webp", find.heroImagePath)
    }

    @Test
    fun `selectFinds includes unidentified observations`() {
        val list = listOf(obs("u", null, daysAgo(1), photoPath = "/p/u.jpg"))
        val find = builder.selectFinds(list, emptyMap(), now).single()
        assertEquals("u", find.observationId)
        assertEquals(null, find.speciesId)
        assertFalse(find.isNewSpecies)
        assertEquals(null, find.heroImagePath)
    }

    @Test
    fun `selectFinds returns empty on quiet week`() {
        assertTrue(builder.selectFinds(emptyList(), emptyMap(), now).isEmpty())
    }
}
