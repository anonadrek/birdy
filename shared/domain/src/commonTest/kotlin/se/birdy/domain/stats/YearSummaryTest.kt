package se.birdy.domain.stats

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import se.birdy.domain.badge.BadgeSeason
import se.birdy.domain.observation.Observation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class YearSummaryTest {
    private val utc = TimeZone.UTC
    private var nextId = 0

    private fun find(
        speciesId: String?,
        iso: String,
    ): Observation {
        val at = Instant.parse(iso)
        return Observation(
            id = "obs-${nextId++}",
            speciesId = speciesId,
            capturedAt = at,
            savedAt = at,
            photoPath = "",
            note = "",
            confidence = 0.9f,
            latitude = null,
            longitude = null,
            locationLabel = null,
        )
    }

    @Test
    fun `finds are counted per month of the year with January first`() {
        val summary =
            summarizeYear(
                listOf(
                    find("Q1", "2026-01-10T08:00:00Z"),
                    find("Q1", "2026-01-20T08:00:00Z"),
                    find("Q2", "2026-05-12T08:00:00Z"),
                    find("Q3", "2026-12-31T08:00:00Z"),
                ),
                year = 2026,
                zone = utc,
            )
        assertEquals(listOf(2, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 1), summary.findsPerMonth)
        assertEquals(4, summary.totalFinds)
        assertEquals(2026, summary.year)
    }

    @Test
    fun `finds from other years are left out`() {
        val summary =
            summarizeYear(
                listOf(
                    find("Q1", "2025-12-31T08:00:00Z"),
                    find("Q2", "2026-03-10T08:00:00Z"),
                    find("Q3", "2027-01-01T08:00:00Z"),
                ),
                year = 2026,
                zone = utc,
            )
        assertEquals(1, summary.totalFinds)
        assertEquals(1, summary.findsPerMonth[2])
        assertEquals(listOf("Q2"), summary.firstSightings.map { it.speciesId })
    }

    @Test
    fun `the time zone decides the month and year of a find near midnight`() {
        // 23:30 UTC on 31 January is already 1 February in Stockholm, and 23:30 UTC on
        // 31 December 2025 is already 2026 there.
        val finds =
            listOf(
                find("Q1", "2026-01-31T23:30:00Z"),
                find("Q2", "2025-12-31T23:30:00Z"),
            )
        val stockholm = summarizeYear(finds, year = 2026, zone = TimeZone.of("Europe/Stockholm"))
        assertEquals(1, stockholm.findsPerMonth[0])
        assertEquals(1, stockholm.findsPerMonth[1])
        assertEquals(2, stockholm.totalFinds)

        val inUtc = summarizeYear(finds, year = 2026, zone = utc)
        assertEquals(1, inUtc.findsPerMonth[0])
        assertEquals(0, inUtc.findsPerMonth[1])
        assertEquals(1, inUtc.totalFinds)
    }

    @Test
    fun `finds of unknown species count but get no first sighting`() {
        val summary =
            summarizeYear(
                listOf(
                    find(null, "2026-02-01T08:00:00Z"),
                    find("Q1", "2026-02-02T08:00:00Z"),
                ),
                year = 2026,
                zone = utc,
            )
        assertEquals(2, summary.findsPerMonth[1])
        assertEquals(listOf("Q1"), summary.firstSightings.map { it.speciesId })
    }

    @Test
    fun `first sightings keep each species earliest find of the year in time order`() {
        val summary =
            summarizeYear(
                listOf(
                    find("Q2", "2026-05-03T08:00:00Z"),
                    find("Q1", "2026-03-14T08:00:00Z"),
                    find("Q1", "2026-01-08T08:00:00Z"),
                    find("Q3", "2026-05-11T08:00:00Z"),
                    find("Q2", "2026-02-21T08:00:00Z"),
                ),
                year = 2026,
                zone = utc,
            )
        assertEquals(
            listOf(
                FirstSighting("Q1", Instant.parse("2026-01-08T08:00:00Z")),
                FirstSighting("Q2", Instant.parse("2026-02-21T08:00:00Z")),
                FirstSighting("Q3", Instant.parse("2026-05-11T08:00:00Z")),
            ),
            summary.firstSightings,
        )
    }

    @Test
    fun `a species also seen last year still gets a first sighting this year`() {
        val summary =
            summarizeYear(
                listOf(
                    find("Q1", "2025-06-01T08:00:00Z"),
                    find("Q1", "2026-04-02T08:00:00Z"),
                ),
                year = 2026,
                zone = utc,
            )
        assertEquals(listOf(FirstSighting("Q1", Instant.parse("2026-04-02T08:00:00Z"))), summary.firstSightings)
    }

    @Test
    fun `first sightings at the same instant are ordered by species id`() {
        val summary =
            summarizeYear(
                listOf(
                    find("Q9", "2026-04-02T08:00:00Z"),
                    find("Q10", "2026-04-02T08:00:00Z"),
                ),
                year = 2026,
                zone = utc,
            )
        assertEquals(listOf("Q10", "Q9"), summary.firstSightings.map { it.speciesId })
    }

    @Test
    fun `the best month is the month with the most finds`() {
        val summary =
            summarizeYear(
                listOf(
                    find("Q1", "2026-01-10T08:00:00Z"),
                    find("Q1", "2026-05-01T08:00:00Z"),
                    find("Q2", "2026-05-02T08:00:00Z"),
                    find("Q3", "2026-05-03T08:00:00Z"),
                    find("Q1", "2026-10-03T08:00:00Z"),
                ),
                year = 2026,
                zone = utc,
            )
        assertEquals(5, summary.bestMonth)
    }

    @Test
    fun `a single find makes its month the best month`() {
        val summary = summarizeYear(listOf(find("Q1", "2026-07-04T08:00:00Z")), year = 2026, zone = utc)
        assertEquals(7, summary.bestMonth)
    }

    @Test
    fun `there is no best month when two months share the top count`() {
        val summary =
            summarizeYear(
                listOf(
                    find("Q1", "2026-01-10T08:00:00Z"),
                    find("Q2", "2026-03-10T08:00:00Z"),
                ),
                year = 2026,
                zone = utc,
            )
        assertNull(summary.bestMonth)
    }

    @Test
    fun `season sums add up the months of each meteorological season`() {
        val summary =
            summarizeYear(
                listOf(
                    find("Q1", "2026-01-10T08:00:00Z"),
                    find("Q1", "2026-02-10T08:00:00Z"),
                    find("Q1", "2026-03-10T08:00:00Z"),
                    find("Q1", "2026-06-10T08:00:00Z"),
                    find("Q1", "2026-08-31T08:00:00Z"),
                    find("Q1", "2026-11-30T08:00:00Z"),
                    // December counts toward this year's winter, like January and February.
                    find(null, "2026-12-24T08:00:00Z"),
                ),
                year = 2026,
                zone = utc,
            )
        assertEquals(3, summary.findsIn(BadgeSeason.WINTER))
        assertEquals(1, summary.findsIn(BadgeSeason.SPRING))
        assertEquals(2, summary.findsIn(BadgeSeason.SUMMER))
        assertEquals(1, summary.findsIn(BadgeSeason.AUTUMN))
    }

    @Test
    fun `an empty year has twelve empty months and nothing else`() {
        val summary = summarizeYear(emptyList(), year = 2026, zone = utc)
        assertEquals(List(12) { 0 }, summary.findsPerMonth)
        assertEquals(0, summary.totalFinds)
        assertTrue(summary.firstSightings.isEmpty())
        assertNull(summary.bestMonth)
        assertEquals(0, summary.findsIn(BadgeSeason.WINTER))
    }
}
