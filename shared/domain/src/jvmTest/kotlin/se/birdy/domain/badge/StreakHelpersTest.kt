package se.birdy.domain.badge

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals

class StreakHelpersTest {
    private val utc = TimeZone.UTC

    private fun instant(
        y: Int,
        m: Int,
        d: Int,
    ): Instant = LocalDateTime(y, m, d, 12, 0).toInstant(utc)

    @Test
    fun `weekKey for thursday returns iso year and week`() {
        // 2026-05-07 är torsdag, vecka 19
        assertEquals(WeekKey(2026, 19), weekKey(instant(2026, 5, 7), utc))
    }

    @Test
    fun `weekKey crosses year — 2026-12-29 is iso week 53 of 2026 not week 1 of 2027`() {
        // 2026-12-29 är tisdag, ISO-vecka 53 (2026 har 53 veckor eftersom 1 jan 2026 är torsdag)
        val key = weekKey(instant(2026, 12, 29), utc)
        assertEquals(2026, key.isoYear)
        assertEquals(53, key.isoWeek)
    }

    @Test
    fun `weekKey for jan 1 2027 (friday) is iso week 53 of 2026`() {
        // 2027-01-01 är fredag, men ISO-veckan tilldelas det år som torsdagen i den veckan ligger i.
        // Veckan 2026-12-28 → 2027-01-03 har torsdag = 2026-12-31, så det är ISO 2026/v53.
        val key = weekKey(instant(2027, 1, 1), utc)
        assertEquals(2026, key.isoYear)
        assertEquals(53, key.isoWeek)
    }

    @Test
    fun `weekKey next handles year-wrap`() {
        val k53 = WeekKey(2026, 53)
        assertEquals(WeekKey(2027, 1), k53.next())
    }

    @Test
    fun `monthKey simple`() {
        assertEquals(MonthKey(2026, 5), monthKey(instant(2026, 5, 7), utc))
    }

    @Test
    fun `monthKey next wraps year`() {
        assertEquals(MonthKey(2027, 1), MonthKey(2026, 12).next())
    }

    @Test
    fun `seasonOf meteorological — march is spring`() {
        assertEquals(BadgeSeason.SPRING, seasonOf(instant(2026, 3, 1), utc))
    }

    @Test
    fun `seasonOf meteorological — december is winter`() {
        assertEquals(BadgeSeason.WINTER, seasonOf(instant(2026, 12, 1), utc))
    }

    @Test
    fun `seasonOf — all 12 months tabular`() {
        val expected =
            mapOf(
                1 to BadgeSeason.WINTER,
                2 to BadgeSeason.WINTER,
                3 to BadgeSeason.SPRING,
                4 to BadgeSeason.SPRING,
                5 to BadgeSeason.SPRING,
                6 to BadgeSeason.SUMMER,
                7 to BadgeSeason.SUMMER,
                8 to BadgeSeason.SUMMER,
                9 to BadgeSeason.AUTUMN,
                10 to BadgeSeason.AUTUMN,
                11 to BadgeSeason.AUTUMN,
                12 to BadgeSeason.WINTER,
            )
        for ((month, season) in expected) {
            assertEquals(season, seasonOf(instant(2026, month, 15), utc), "month=$month")
        }
    }

    @Test
    fun `longestWeeklyStreak — empty list returns 0`() {
        assertEquals(0, longestWeeklyStreak(emptyList(), utc))
    }

    @Test
    fun `longestWeeklyStreak — single observation returns 1`() {
        assertEquals(1, longestWeeklyStreak(listOf(instant(2026, 5, 7)), utc))
    }

    @Test
    fun `longestWeeklyStreak — same week twice counts as 1`() {
        // Båda 2026-05-04 (mån) och 2026-05-07 (tor) är v19/2026
        val obs = listOf(instant(2026, 5, 4), instant(2026, 5, 7))
        assertEquals(1, longestWeeklyStreak(obs, utc))
    }

    @Test
    fun `longestWeeklyStreak — three consecutive weeks returns 3`() {
        val obs =
            listOf(
                instant(2026, 5, 4), // v19
                instant(2026, 5, 11), // v20
                instant(2026, 5, 18), // v21
            )
        assertEquals(3, longestWeeklyStreak(obs, utc))
    }

    @Test
    fun `longestWeeklyStreak — gap breaks streak`() {
        val obs =
            listOf(
                instant(2026, 5, 4), // v19
                instant(2026, 5, 11), // v20
                // hoppa över v21
                instant(2026, 5, 25), // v22
                instant(2026, 6, 1), // v23
            )
        assertEquals(2, longestWeeklyStreak(obs, utc))
    }

    @Test
    fun `longestWeeklyStreak — cross-year v53 to v01`() {
        val obs =
            listOf(
                instant(2026, 12, 22), // v52/2026
                instant(2026, 12, 29), // v53/2026
                instant(2027, 1, 5), // v01/2027
            )
        assertEquals(3, longestWeeklyStreak(obs, utc))
    }

    @Test
    fun `longestMonthlyStreak — three consecutive months`() {
        val obs =
            listOf(
                instant(2026, 3, 15),
                instant(2026, 4, 15),
                instant(2026, 5, 15),
            )
        assertEquals(3, longestMonthlyStreak(obs, utc))
    }

    @Test
    fun `longestMonthlyStreak — cross-year`() {
        val obs =
            listOf(
                instant(2026, 11, 15),
                instant(2026, 12, 15),
                instant(2027, 1, 15),
            )
        assertEquals(3, longestMonthlyStreak(obs, utc))
    }

    @Test
    fun `longestConsecutive — generic helper handles empty`() {
        assertEquals(0, longestConsecutive(emptyList<Int>()) { it + 1 })
    }

    @Test
    fun `isoWeeksInYear — 2026 has 53 weeks`() {
        // 1 jan 2026 = torsdag → 53 ISO-veckor
        assertEquals(53, isoWeeksInYear(2026))
    }

    @Test
    fun `isoWeeksInYear — 2025 has 52 weeks`() {
        assertEquals(52, isoWeeksInYear(2025))
    }
}
