package se.birdy.data.observation

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import app.cash.sqldelight.driver.native.wrapConnection
import app.cash.turbine.test
import co.touchlab.sqliter.DatabaseConfiguration
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import se.birdy.data.db.BirdyData
import se.birdy.domain.observation.Observation
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Runs the real observation repository against an in-memory instance of the
 * SQLDelight *native* driver — the driver the app uses on device — to prove the
 * save -> persist -> read round-trip works on iOS. (The user-facing save UI is
 * deferred to i2 with the camera; this locks the persistence layer in now.)
 */
class SqlDelightObservationRepositoryIosTest {
    private val drivers = mutableListOf<SqlDriver>()

    private fun newRepo(): SqlDelightObservationRepository {
        val driver =
            NativeSqliteDriver(
                DatabaseConfiguration(
                    name = "test-observations.db",
                    version = BirdyData.Schema.version.toInt(),
                    inMemory = true,
                    create = { conn -> wrapConnection(conn) { BirdyData.Schema.create(it) } },
                    upgrade = { _, _, _ -> },
                ),
            )
        drivers += driver
        return SqlDelightObservationRepository(BirdyData(driver).observationQueries)
    }

    @AfterTest
    fun closeDrivers() {
        drivers.forEach { it.close() }
        drivers.clear()
    }

    private fun sample(
        id: String,
        capturedAtMs: Long,
        speciesId: String = "Q25485",
    ) = Observation(
        id = id,
        speciesId = speciesId,
        capturedAt = Instant.fromEpochMilliseconds(capturedAtMs),
        savedAt = Instant.fromEpochMilliseconds(capturedAtMs + 1_000),
        photoPath = "/tmp/$id.jpg",
        note = "",
        confidence = 0.87f,
        latitude = null,
        longitude = null,
        locationLabel = null,
    )

    @Test
    fun insert_then_observeAll_emits_inserted_row_on_native_driver() =
        runTest {
            val repo = newRepo()
            repo.insert(sample("a", 1_000L))
            repo.observeAll().test {
                val rows = awaitItem()
                assertEquals(1, rows.size)
                assertEquals("a", rows[0].id)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun insert_round_trips_optional_geotag_fields_on_native_driver() =
        runTest {
            val repo = newRepo()
            repo.insert(
                sample("c", 1_000L).copy(
                    latitude = 59.33,
                    longitude = 18.07,
                    locationLabel = "Stockholm",
                ),
            )
            repo.observeById("c").test {
                val row = awaitItem()!!
                assertEquals(59.33, row.latitude)
                assertEquals(18.07, row.longitude)
                assertEquals("Stockholm", row.locationLabel)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun observeById_rebases_stale_sandbox_photo_path_onto_current_documents() =
        runTest {
            val repo = newRepo()
            val docs = currentDocumentsDirectory()
            assertTrue(docs.isNotEmpty(), "iosTest must resolve a live Documents directory")
            val stale =
                "/var/mobile/Containers/Data/Application/" +
                    "DEADBEEF-0000-0000-0000-000000000000/Documents/observations/restored.jpg"
            repo.insert(sample("r", 1_000L).copy(photoPath = stale))
            repo.observeById("r").test {
                val row = awaitItem()!!
                assertEquals("$docs/observations/restored.jpg", row.photoPath)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun delete_cleanup_paths_are_rebased_to_current_container() =
        runTest {
            val repo = newRepo()
            val docs = currentDocumentsDirectory()
            val stalePhoto =
                "/var/mobile/Containers/Data/Application/" +
                    "DEADBEEF-0000-0000-0000-000000000000/Documents/observations/p.jpg"
            val staleAudio =
                "/var/mobile/Containers/Data/Application/" +
                    "DEADBEEF-0000-0000-0000-000000000000/Documents/audio/a.opus"
            repo.insert(
                sample("r", 1_000L).copy(
                    photoPath = stalePhoto,
                    audioPath = staleAudio,
                ),
            )
            val cleanup = repo.delete("r")
            assertEquals("$docs/observations/p.jpg", cleanup.photoPath)
            assertEquals("$docs/audio/a.opus", cleanup.audioPath)
        }
}
