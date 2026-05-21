package se.birdy.data.observation

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import se.birdy.data.db.BirdyData

class StampNumberMigrationTest {
    @Test
    fun `migration backfills stamp_number chronologically`() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        driver.execute(null, V1_SCHEMA, 0)
        val rows =
            listOf(
                "obs-c" to 3000L,
                "obs-a" to 1000L,
                "obs-e" to 5000L,
                "obs-b" to 2000L,
                "obs-d" to 4000L,
            )
        rows.forEach { (id, captured) ->
            driver.execute(
                identifier = null,
                sql =
                    """
                    INSERT INTO observation (id, species_id, captured_at_ms, saved_at_ms,
                        photo_path, note, confidence)
                    VALUES (?, 'Q1', ?, ?, '/tmp/x.jpg', '', 0.9)
                    """.trimIndent(),
                parameters = 3,
            ) {
                bindString(0, id)
                bindLong(1, captured)
                bindLong(2, captured)
            }
        }

        BirdyData.Schema.migrate(driver, oldVersion = 1, newVersion = 2)

        val cursor =
            driver
                .executeQuery(
                    identifier = null,
                    sql = "SELECT id, stamp_number FROM observation ORDER BY stamp_number",
                    mapper = { c ->
                        val results = mutableListOf<Pair<String, Long>>()
                        while (c.next().value) {
                            results.add(c.getString(0)!! to c.getLong(1)!!)
                        }
                        app.cash.sqldelight.db.QueryResult
                            .Value(results.toList())
                    },
                    parameters = 0,
                ).value

        assertEquals(
            listOf(
                "obs-a" to 1L,
                "obs-b" to 2L,
                "obs-c" to 3L,
                "obs-d" to 4L,
                "obs-e" to 5L,
            ),
            cursor,
        )
        driver.close()
    }

    @Test
    fun `insert after migration continues sequence at MAX+1`() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        driver.execute(null, V1_SCHEMA, 0)
        // Seed 3 V1 rows
        listOf("a" to 1000L, "b" to 2000L, "c" to 3000L).forEach { (id, captured) ->
            driver.execute(
                identifier = null,
                sql =
                    """
                    INSERT INTO observation (id, species_id, captured_at_ms, saved_at_ms,
                        photo_path, note, confidence)
                    VALUES (?, 'Q1', ?, ?, '/tmp/x.jpg', '', 0.9)
                    """.trimIndent(),
                parameters = 3,
            ) {
                bindString(0, id)
                bindLong(1, captured)
                bindLong(2, captured)
            }
        }
        // Migrate from v1 all the way to current schema version (v4)
        BirdyData.Schema.migrate(driver, oldVersion = 1, newVersion = BirdyData.Schema.version)

        // Now use the SQLDelight-generated insert via the BirdyData wrapper
        val db = BirdyData(driver)
        db.observationQueries.insert(
            id = "d",
            species_id = "Q1",
            captured_at_ms = 4000L,
            saved_at_ms = 4000L,
            photo_path = "/tmp/d.jpg",
            note = "",
            confidence = 0.95,
            latitude = null,
            longitude = null,
            location_label = null,
            stamp_number = db.observationQueries.nextStampNumber().executeAsOne(),
            audio_path = null,
            source = "photo",
        )

        val newStamp =
            driver
                .executeQuery(
                    identifier = null,
                    sql = "SELECT stamp_number FROM observation WHERE id = 'd'",
                    mapper = { c ->
                        c.next()
                        app.cash.sqldelight.db.QueryResult
                            .Value(c.getLong(0)!!)
                    },
                    parameters = 0,
                ).value

        assertEquals(4L, newStamp)
        driver.close()
    }

    private companion object {
        private const val V1_SCHEMA = """
            CREATE TABLE observation (
                id              TEXT NOT NULL PRIMARY KEY,
                species_id      TEXT NOT NULL,
                captured_at_ms  INTEGER NOT NULL,
                saved_at_ms     INTEGER NOT NULL,
                photo_path      TEXT NOT NULL,
                note            TEXT NOT NULL DEFAULT '',
                confidence      REAL NOT NULL,
                latitude        REAL,
                longitude       REAL,
                location_label  TEXT
            );
            CREATE INDEX observation_captured_at_idx ON observation(captured_at_ms DESC);
        """
    }
}
