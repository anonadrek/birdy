package se.birdy.domain.notification

interface NotificationScheduler {
    fun scheduleDailyBird()

    fun scheduleWeeklyRecap()

    fun cancelDailyBird()

    fun cancelStreakRiskCheck()

    fun cancelWeeklyRecap()
}
