package se.birdy.data

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import se.birdy.data.db.BirdyData

actual class DatabaseFactory(
    private val context: Context,
) {
    actual fun createDriver(): SqlDriver = AndroidSqliteDriver(BirdyData.Schema, context, "birdy-observations.db")
}
