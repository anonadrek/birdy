package se.birdy.app.recap

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
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
        val list = listOf(
            obs("a", "Q1", daysAgo(1)), // denna vecka
            obs("b", "Q2", daysAgo(2)), // denna vecka
            obs("c", "Q3", daysAgo(9)), // förra veckan
        )
        val s = builder.summarize(list, emptyList(), now)
        assertEquals(2, s.observationCount)
        assertEquals(1, s.deltaVsLastWeek) // 2 - 1
    }

    @Test
    fun `new species counted only when first sighting falls this week`() {
        val list = listOf(
            obs("old", "Q1", daysAgo(30)), // Q1 sågs först förra månaden → ej ny
            obs("now1", "Q1", daysAgo(1)),
            obs("now2", "Q2", daysAgo(1)), // Q2 helt ny denna vecka
        )
        val s = builder.summarize(list, emptyList(), now)
        assertEquals(1, s.newSpeciesCount)
    }

    @Test
    fun `badges unlocked this week are listed`() {
        val unlocks = listOf(
            BadgeUnlock("premium_early_pilgrim", daysAgo(1)),
            BadgeUnlock("weekly_5", daysAgo(20)), // förra månaden
        )
        val s = builder.summarize(listOf(obs("a", "Q1", daysAgo(1))), unlocks, now)
        assertEquals(listOf("premium_early_pilgrim"), s.newBadgeIds)
    }

    @Test
    fun `streak at risk when quiet week but prior weeks active`() {
        val list = listOf(
            obs("w21", "Q1", daysAgo(8)),  // förra veckan
            obs("w20", "Q1", daysAgo(15)), // veckan innan
        )
        val s = builder.summarize(list, emptyList(), now)
        assertEquals(0, s.observationCount)
        assertEquals(2, s.weeklyStreak)
        assertTrue(s.streakAtRisk)
    }
}
