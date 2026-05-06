package se.birdy.data

import app.cash.sqldelight.db.SqlDriver

expect class DatabaseFactory {
    fun createDriver(): SqlDriver
}
