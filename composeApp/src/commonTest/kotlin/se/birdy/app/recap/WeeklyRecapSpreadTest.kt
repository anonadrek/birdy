package se.birdy.app.recap

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import se.birdy.domain.observation.Observation
import se.birdy.domain.observation.ObservationSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Release 1.3.0 Task 7j ("Uppslag 1", Albin's choice 2026-10-07): the data behind the new weekly
 * spread. The week is still the ISO week of "now" (Monday to Sunday, as before); what is new is
 * the Monday-to-Sunday strip, the new species with their place in the life list and the find
 * fields the grid needs. Days are local dates in the zone, so a find near midnight lands on the
 * day the user saw it, also in the weeks when the clocks change.
 */
class WeeklyRecapSpreadTest {
    private val stockholm = TimeZone.of("Europe/Stockholm")

    private fun obs(
        id: String,
        speciesId: String?,
        at: String,
        stampNumber: Int = 0,
        photoPath: String = "/p/$id.jpg",
        source: ObservationSource = ObservationSource.Photo,
    ) = Observation(
        id = id,
        speciesId = speciesId,
        capturedAt = Instant.parse(at),
        savedAt = Instant.parse(at),
        photoPath = photoPath,
        note = "",
        confidence = 0.9f,
        latitude = null,
        longitude = null,
        locationLabel = null,
        stampNumber = stampNumber,
        sourceType = source,
    )

    private fun build(
        observations: List<Observation>,
        now: String,
        zone: TimeZone = stockholm,
    ): WeeklyRecap = WeeklyRecapBuilder(zone).build(observations, emptyMap(), emptyList(), Instant.parse(now))

    // ── The Monday-to-Sunday strip ─────────────────────────────────────────────────────────────

    @Test
    fun `the strip runs Monday to Sunday of the current week`() {
        // Wednesday 7 October 2026, ISO week 41 (Monday 5 to Sunday 11 October).
        val recap = build(emptyList(), now = "2026-10-07T10:00:00Z")
        assertEquals(
            (5..11).map { LocalDate(2026, 10, it) },
            recap.days.map { it.date },
        )
        assertTrue(recap.days.all { it.findCount == 0 && it.cover == null })
    }

    @Test
    fun `the strip counts each day's finds and shows the day's latest find`() {
        val recap =
            build(
                listOf(
                    obs("mon", "Q1", "2026-10-05T07:00:00Z"),
                    obs("sat-early", "Q2", "2026-10-10T08:00:00Z"),
                    obs("sat-late", "Q3", "2026-10-10T15:00:00Z"),
                    obs("last-week", "Q4", "2026-10-04T12:00:00Z"),
                ),
                now = "2026-10-11T16:00:00Z",
            )
        assertEquals(listOf(1, 0, 0, 0, 0, 2, 0), recap.days.map { it.findCount })
        assertEquals("mon", recap.days[0].cover?.observationId)
        assertEquals("sat-late", recap.days[5].cover?.observationId)
        assertNull(recap.days[6].cover)
    }

    @Test
    fun `a find just after midnight counts for the local day and not the UTC day`() {
        // 00:30 on Monday 5 October in Stockholm is still Sunday 4 October in UTC.
        val recap = build(listOf(obs("night", "Q1", "2026-10-04T22:30:00Z")), now = "2026-10-07T10:00:00Z")
        assertEquals(1, recap.days.first().findCount)
        assertEquals(1, recap.summary.observationCount)
    }

    @Test
    fun `on Monday just after midnight the strip shows the new week and not last Sunday`() {
        // Now: Monday 12 October 00:10 in Stockholm. Last night's find (Sunday 23:50) belongs to week 41.
        val recap =
            build(
                listOf(obs("sunday-night", "Q1", "2026-10-11T21:50:00Z")),
                now = "2026-10-11T22:10:00Z",
            )
        assertEquals(LocalDate(2026, 10, 12), recap.days.first().date)
        assertEquals(LocalDate(2026, 10, 18), recap.days.last().date)
        assertTrue(recap.days.all { it.findCount == 0 })
        assertTrue(recap.finds.isEmpty())
    }

    @Test
    fun `the week the clocks go forward still ends at Sunday midnight`() {
        // ISO week 13 2026: Monday 23 to Sunday 29 March; summer time starts 29 March at 02:00.
        // The week is 167 hours long, so "Monday 00:00 plus seven times 24 hours" would wrongly
        // take in the first hour of Monday 30 March.
        val recap =
            build(
                listOf(
                    obs("sunday-late", "Q1", "2026-03-29T21:30:00Z"), // Sunday 23:30 CEST
                    obs("next-monday", "Q2", "2026-03-29T22:30:00Z"), // Monday 30 March 00:30 CEST
                ),
                now = "2026-03-29T20:00:00Z",
            )
        assertEquals(LocalDate(2026, 3, 29), recap.days.last().date)
        assertEquals(1, recap.days.last().findCount)
        assertEquals(listOf("sunday-late"), recap.finds.map { it.observationId })
    }

    @Test
    fun `the week the clocks go back keeps its last hour`() {
        // ISO week 43 2026: Monday 19 to Sunday 25 October; winter time starts 25 October at 03:00,
        // so this week is 169 hours long and Sunday 23:30 CET is 22:30 UTC.
        val recap =
            build(
                listOf(
                    obs("sunday-last-hour", "Q1", "2026-10-25T22:30:00Z"), // Sunday 23:30 CET
                    obs("next-monday", "Q2", "2026-10-25T23:30:00Z"), // Monday 26 October 00:30 CET
                ),
                now = "2026-10-25T21:00:00Z",
            )
        assertEquals(LocalDate(2026, 10, 19), recap.days.first().date)
        assertEquals(1, recap.days.last().findCount)
        assertEquals(listOf("sunday-last-hour"), recap.finds.map { it.observationId })
    }

    @Test
    fun `days after today are marked as still to come`() {
        // Wednesday 7 October: Thursday to Sunday have not happened yet.
        val recap = build(emptyList(), now = "2026-10-07T10:00:00Z")
        assertEquals(listOf(false, false, false, true, true, true, true), recap.days.map { it.isFuture })
    }

    // ── New species and their place in the life list ───────────────────────────────────────────

    @Test
    fun `new species get their life-list number in the order of first sightings`() {
        val recap =
            build(
                listOf(
                    obs("a", "Q10", "2026-09-01T10:00:00Z"), // № 1
                    obs("b", "Q20", "2026-09-02T10:00:00Z"), // № 2
                    obs("b2", "Q20", "2026-10-06T10:00:00Z"), // seen again this week: not new
                    obs("c", "Q30", "2026-10-06T09:00:00Z"), // № 3, Tuesday
                    obs("d", "Q40", "2026-10-08T09:00:00Z"), // № 4, Thursday
                    obs("d2", "Q40", "2026-10-09T09:00:00Z"), // its second find this week
                    obs("e", "Q50", "2026-10-10T09:00:00Z"), // № 5, Saturday
                ),
                now = "2026-10-11T16:00:00Z",
            )
        // Newest first, as in the design (№ 31, 30, 29).
        assertEquals(listOf("Q50", "Q40", "Q30"), recap.newSpecies.map { it.speciesId })
        assertEquals(listOf(5, 4, 3), recap.newSpecies.map { it.lifeListNumber })
        // The find that put the species on the list, not its later one.
        assertEquals(
            "d",
            recap.newSpecies
                .single { it.speciesId == "Q40" }
                .firstFind.observationId,
        )
        assertEquals(3, recap.summary.newSpeciesCount)
    }

    @Test
    fun `two species first seen at the same moment are numbered by species id`() {
        val recap =
            build(
                listOf(
                    obs("y", "Q9", "2026-10-06T10:00:00Z"),
                    obs("x", "Q1", "2026-10-06T10:00:00Z"),
                ),
                now = "2026-10-11T16:00:00Z",
            )
        assertEquals(mapOf("Q1" to 1, "Q9" to 2), recap.newSpecies.associate { it.speciesId to it.lifeListNumber })
    }

    @Test
    fun `an unknown find is never a new species and does not take a life-list number`() {
        val recap =
            build(
                listOf(
                    obs("u", null, "2026-10-05T10:00:00Z"),
                    obs("a", "Q10", "2026-10-06T10:00:00Z"),
                ),
                now = "2026-10-11T16:00:00Z",
            )
        assertEquals(listOf("Q10"), recap.newSpecies.map { it.speciesId })
        assertEquals(1, recap.newSpecies.single().lifeListNumber)
        assertFalse(recap.finds.single { it.observationId == "u" }.isNewSpecies)
    }

    @Test
    fun `a quiet week has no new species`() {
        val recap = build(listOf(obs("old", "Q1", "2026-09-01T10:00:00Z")), now = "2026-10-07T10:00:00Z")
        assertTrue(recap.newSpecies.isEmpty())
        assertTrue(recap.summary.isQuiet)
    }

    // ── The finds for the grid ────────────────────────────────────────────────────────────────

    @Test
    fun `each find carries when it was seen and whether it was heard`() {
        val recap =
            build(
                listOf(obs("au", "Q1", "2026-10-06T10:00:00Z", photoPath = "/a/wave.png", source = ObservationSource.Audio)),
                now = "2026-10-11T16:00:00Z",
            )
        val find = recap.finds.single()
        assertEquals(Instant.parse("2026-10-06T10:00:00Z"), find.capturedAt)
        assertEquals(ObservationSource.Audio, find.source)
    }
}
