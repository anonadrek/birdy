package se.birdy.app.ui.dailybird

import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.daily_bird_weekday_tue
import birdy_bird_scanner.composeapp.generated.resources.diary_month_short_oct
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * The hero kicker's date ("DAGENS FÅGEL · TIS 6 OKT"). The rendered text in both languages is
 * checked under Robolectric (DailyBirdHeroTest); this pins the date-to-resource mapping.
 */
class DailyBirdDateTest {
    @Test
    fun `tuesday 6 october maps to the tuesday and october short names`() {
        val parts = dailyBirdDateParts(LocalDate(2026, 10, 6))
        assertSame(Res.string.daily_bird_weekday_tue, parts.weekday)
        assertEquals(6, parts.dayOfMonth)
        assertSame(Res.string.diary_month_short_oct, parts.month)
    }

    @Test
    fun `every weekday has its own short name`() {
        val names = DayOfWeek.entries.map { weekdayShortRes(it) }
        assertEquals(7, names.toSet().size)
    }

    @Test
    fun `every month has its own short name`() {
        val names = Month.entries.map { monthShortRes(it) }
        assertEquals(12, names.toSet().size)
    }

    @Test
    fun `the day of month is not padded`() {
        assertEquals(1, dailyBirdDateParts(LocalDate(2026, 11, 1)).dayOfMonth)
    }
}
