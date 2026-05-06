package se.birdy.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import se.birdy.data.db.BirdyData

actual class DatabaseFactory(
    private val jdbcUrl: String = JdbcSqliteDriver.IN_MEMORY,
) {
    actual fun createDriver(): SqlDriver {
        val driver = JdbcSqliteDriver(jdbcUrl)
        BirdyData.Schema.create(driver)
        return driver
    }
}
