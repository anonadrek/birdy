package se.birdy.domain.notification

interface NotificationScheduler {
    fun scheduleDailyBird()

    fun scheduleWeeklyRecap()

    fun scheduleTrophyProgress()

    fun cancelDailyBird()

    fun cancelStreakRiskCheck()

    fun cancelWeeklyRecap()

    fun cancelTrophyProgress()
}
