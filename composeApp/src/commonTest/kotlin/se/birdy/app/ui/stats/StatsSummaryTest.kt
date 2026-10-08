package se.birdy.app.ui.stats

import kotlin.test.Test
import kotlin.test.assertEquals

class StatsSummaryTest {
    @Test
    fun `one find names the month it came in`() {
        assertEquals(StatsSummary.FirstFind(month = 3), statsSummaryFor(totalFinds = 1, bestMonth = 3, currentMonth = 10))
    }

    @Test
    fun `a past best month is named with the total`() {
        assertEquals(
            StatsSummary.BestMonth(totalFinds = 15, month = 5, isCurrentMonth = false),
            statsSummaryFor(totalFinds = 15, bestMonth = 5, currentMonth = 10),
        )
    }

    @Test
    fun `the current month as best month is marked so the sentence can say so far`() {
        assertEquals(
            StatsSummary.BestMonth(totalFinds = 4, month = 10, isCurrentMonth = true),
            statsSummaryFor(totalFinds = 4, bestMonth = 10, currentMonth = 10),
        )
    }

    @Test
    fun `a tie for the best month gives only the total`() {
        assertEquals(StatsSummary.TotalOnly(totalFinds = 2), statsSummaryFor(totalFinds = 2, bestMonth = null, currentMonth = 10))
    }
}
