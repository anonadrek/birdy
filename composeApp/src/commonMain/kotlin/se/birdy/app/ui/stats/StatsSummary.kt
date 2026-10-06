package se.birdy.app.ui.stats

/**
 * Which sentence the Season Statistics intro shows under "Ett år i fält." (release 1.3.0 Task 7c):
 * computed from the year's data, never a fixed line. The screen turns it into text with the
 * matching string resource.
 */
internal sealed interface StatsSummary {
    /** Exactly one find this year: "Ditt första fynd i år kom i mars." */
    data class FirstFind(
        val month: Int,
    ) : StatsSummary

    /** One month has more finds than any other: "15 fynd. Maj var din bästa månad." */
    data class BestMonth(
        val totalFinds: Int,
        val month: Int,
        /** The best month is still going on: the sentence says "so far" instead of "was". */
        val isCurrentMonth: Boolean,
    ) : StatsSummary

    /** Two or more months share the top count, so no month is named: "2 fynd hittills i år." */
    data class TotalOnly(
        val totalFinds: Int,
    ) : StatsSummary
}

internal fun statsSummaryFor(
    totalFinds: Int,
    bestMonth: Int?,
    currentMonth: Int?,
): StatsSummary =
    when {
        totalFinds == 1 && bestMonth != null -> StatsSummary.FirstFind(bestMonth)
        bestMonth != null -> StatsSummary.BestMonth(totalFinds, bestMonth, isCurrentMonth = bestMonth == currentMonth)
        else -> StatsSummary.TotalOnly(totalFinds)
    }
