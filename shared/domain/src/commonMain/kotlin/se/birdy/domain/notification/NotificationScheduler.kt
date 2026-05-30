package se.birdy.domain.notification

interface NotificationScheduler {
    fun scheduleDailyBird()

    fun scheduleStreakRiskCheck()

    fun scheduleWeeklyRecap()

    fun cancelDailyBird()

    fun cancelStreakRiskCheck()

    fun cancelWeeklyRecap()
}
