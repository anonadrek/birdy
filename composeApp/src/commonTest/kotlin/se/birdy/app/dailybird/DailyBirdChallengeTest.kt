package se.birdy.app.dailybird

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The challenge row under the hero and in the strips. The count is distinct days on which the
 * user saved that day's bird (DailyBirdHistoryRepository.totalMatchCount), not days in a row; the
 * target 3 is the Dagens fågel-jägare badge (premium_badges.yaml, daily_bird_matches target 3).
 */
class DailyBirdChallengeTest {
    @Test
    fun `not caught today with no days asks for a find today and shows three empty seals`() {
        val c = dailyBirdChallenge(caughtToday = false, daysCaught = 0, target = 3)
        assertEquals(DailyBirdChallengeLine.SAVE_A_FIND_TODAY, c.line)
        assertEquals(0, c.filledSeals)
        assertEquals(3, c.seals)
        assertEquals(0, c.daysCaught)
        assertEquals(DailyBirdCountStyle.OF_TARGET, c.countStyle)
        assertEquals(false, c.caughtToday)
    }

    @Test
    fun `not caught today keeps earlier days filled`() {
        val c = dailyBirdChallenge(caughtToday = false, daysCaught = 2, target = 3)
        assertEquals(DailyBirdChallengeLine.SAVE_A_FIND_TODAY, c.line)
        assertEquals(2, c.filledSeals)
    }

    @Test
    fun `caught today on the first day says two days left`() {
        val c = dailyBirdChallenge(caughtToday = true, daysCaught = 1, target = 3)
        assertEquals(true, c.caughtToday)
        assertEquals(DailyBirdChallengeLine.DAYS_LEFT_TO_BADGE, c.line)
        assertEquals(2, c.daysLeft)
        assertEquals(1, c.filledSeals)
        assertEquals(DailyBirdCountStyle.OF_TARGET, c.countStyle)
    }

    @Test
    fun `caught today on the second day says one day left`() {
        val c = dailyBirdChallenge(caughtToday = true, daysCaught = 2, target = 3)
        assertEquals(DailyBirdChallengeLine.DAYS_LEFT_TO_BADGE, c.line)
        assertEquals(1, c.daysLeft)
    }

    @Test
    fun `caught today on the third day says the badge is complete`() {
        val c = dailyBirdChallenge(caughtToday = true, daysCaught = 3, target = 3)
        assertEquals(DailyBirdChallengeLine.BADGE_COMPLETE, c.line)
        assertEquals(0, c.daysLeft)
        assertEquals(3, c.filledSeals)
        assertEquals(DailyBirdCountStyle.OF_TARGET, c.countStyle)
    }

    @Test
    fun `past the target the count shows the real total and the seals stay full`() {
        val c = dailyBirdChallenge(caughtToday = true, daysCaught = 5, target = 3)
        assertEquals(DailyBirdChallengeLine.BADGE_COMPLETE, c.line)
        assertEquals(3, c.filledSeals)
        assertEquals(5, c.daysCaught)
        assertEquals(DailyBirdCountStyle.TOTAL, c.countStyle)
    }

    @Test
    fun `caught today never shows zero days even if the count lags`() {
        val c = dailyBirdChallenge(caughtToday = true, daysCaught = 0, target = 3)
        assertEquals(1, c.daysCaught)
        assertEquals(1, c.filledSeals)
        assertEquals(2, c.daysLeft)
    }

    @Test
    fun `the tracker state maps onto the same challenge`() {
        val bird =
            DailyBirdToday(
                date = kotlinx.datetime.LocalDate(2026, 10, 6),
                speciesId = "Q25403",
                name = "Sävsångare",
                scientificName = "Acrocephalus schoenobaenus",
                heroImage = null,
                caughtToday = true,
                daysCaught = 1,
            )
        assertEquals(dailyBirdChallenge(caughtToday = true, daysCaught = 1, target = 3), bird.challenge())
    }

    // Albin 2026-10-07: Dagens fågel-jägare is a Premium badge; the label decision rides on today's
    // bird (set by AppGraph.dailyBirdForDisplay) so the row and the strips draw the same thing.
    @Test
    fun `the premium badge tag carries over from todays bird to the challenge`() {
        val bird =
            DailyBirdToday(
                date = kotlinx.datetime.LocalDate(2026, 10, 6),
                speciesId = "Q25403",
                name = "Sävsångare",
                scientificName = "Acrocephalus schoenobaenus",
                heroImage = null,
                caughtToday = false,
                daysCaught = 0,
            )
        assertEquals(false, bird.challenge().showPremiumBadgeTag)
        assertEquals(true, bird.copy(showPremiumBadgeTag = true).challenge().showPremiumBadgeTag)
    }
}
