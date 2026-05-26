package se.birdy.domain.notification

interface NotificationScheduler {
    fun scheduleDailyBird()

    fun scheduleStreakRiskCheck()

    fun cancelDailyBird()

    fun cancelStreakRiskCheck()
}
