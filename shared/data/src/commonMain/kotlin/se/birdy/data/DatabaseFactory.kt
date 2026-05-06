package se.birdy.data

import app.cash.sqldelight.db.SqlDriver
import se.birdy.data.db.BirdyData

expect class DatabaseFactory {
    fun createDriver(): SqlDriver
}

fun BirdyData.Companion.create(driver: SqlDriver): BirdyData = BirdyData(driver)
