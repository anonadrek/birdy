package se.birdy.app.dailybird

/** The handwritten line under the challenge status. */
enum class DailyBirdChallengeLine {
    /** Not caught today: "Spara ett fynd av arten idag." */
    SAVE_A_FIND_TODAY,

    /** Caught today, badge not reached yet: "N dagar kvar till märket." */
    DAYS_LEFT_TO_BADGE,

    /** Caught today and the badge target is reached: "Märket är klart." */
    BADGE_COMPLETE,
}

/** How the day count is written. */
enum class DailyBirdCountStyle {
    /** "1 av 3 dagar", while the count is at most the target. */
    OF_TARGET,

    /** "5 dagar", once the count has passed the target ("5 av 3" would be false). */
    TOTAL,
}

/**
 * The challenge row (release 1.3.0 Task 7d, design option B): caught today or not, what to do,
 * three seals and the day count toward Dagens fågel-jägare. [daysCaught] counts distinct days on
 * which that day's bird was saved, all time, so the copy says "dagar", never "i rad".
 */
data class DailyBirdChallenge(
    val caughtToday: Boolean,
    val line: DailyBirdChallengeLine,
    val daysLeft: Int,
    val filledSeals: Int,
    val seals: Int,
    val daysCaught: Int,
    val countStyle: DailyBirdCountStyle,
)

fun dailyBirdChallenge(
    caughtToday: Boolean,
    daysCaught: Int,
    target: Int,
): DailyBirdChallenge {
    // A catch today is itself one day, even if the count was read a moment before the match landed.
    val days = if (caughtToday) daysCaught.coerceAtLeast(1) else daysCaught.coerceAtLeast(0)
    val daysLeft = (target - days).coerceAtLeast(0)
    return DailyBirdChallenge(
        caughtToday = caughtToday,
        line =
            when {
                !caughtToday -> DailyBirdChallengeLine.SAVE_A_FIND_TODAY
                daysLeft > 0 -> DailyBirdChallengeLine.DAYS_LEFT_TO_BADGE
                else -> DailyBirdChallengeLine.BADGE_COMPLETE
            },
        daysLeft = daysLeft,
        filledSeals = days.coerceAtMost(target),
        seals = target,
        daysCaught = days,
        countStyle = if (days <= target) DailyBirdCountStyle.OF_TARGET else DailyBirdCountStyle.TOTAL,
    )
}

fun DailyBirdToday.challenge(): DailyBirdChallenge =
    dailyBirdChallenge(caughtToday = caughtToday, daysCaught = daysCaught, target = huntTarget)
