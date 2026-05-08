package se.birdy.data.observation

import app.cash.sqldelight.db.SqlDriver
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.jupiter.api.AfterEach
import se.birdy.data.DatabaseFactory
import se.birdy.data.db.BirdyData
import se.birdy.domain.observation.Observation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SqlDelightObservationRepositoryTest {
    private val drivers = mutableListOf<SqlDriver>()

    private fun newRepo(): SqlDelightObservationRepository {
        val driver = DatabaseFactory().createDriver()
        drivers += driver
        return SqlDelightObservationRepository(BirdyData(driver).observationQueries)
    }

    @AfterEach
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
    fun insert_then_observeAll_emits_inserted_row() =
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
    fun observeAll_orders_desc_by_captured_at() =
        runTest {
            val repo = newRepo()
            repo.insert(sample("old", 1_000L))
            repo.insert(sample("new", 2_000L))
            repo.observeAll().test {
                val rows = awaitItem()
                assertEquals(listOf("new", "old"), rows.map { it.id })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun observeById_emits_null_when_missing() =
        runTest {
            val repo = newRepo()
            repo.observeById("missing").test {
                assertNull(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun updateNote_changes_emitted_row() =
        runTest {
            val repo = newRepo()
            repo.insert(sample("a", 1_000L))
            repo.updateNote("a", "uppdaterad")
            repo.observeById("a").test {
                val row = awaitItem()
                assertEquals("uppdaterad", row?.note)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun delete_removes_row() =
        runTest {
            val repo = newRepo()
            repo.insert(sample("a", 1_000L))
            repo.delete("a")
            repo.observeAll().test {
                assertEquals(emptyList(), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun insert_round_trips_optional_fields() =
        runTest {
            val repo = newRepo()
            val withCoords =
                sample("c", 1_000L).copy(
                    latitude = 59.33,
                    longitude = 18.07,
                    locationLabel = "Stockholm",
                )
            repo.insert(withCoords)
            repo.observeById("c").test {
                val row = awaitItem()!!
                assertEquals(59.33, row.latitude)
                assertEquals(18.07, row.longitude)
                assertEquals("Stockholm", row.locationLabel)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun insert_assigns_sequential_stamp_number_starting_at_1() =
        runTest {
            val repo = newRepo()
            repo.insert(sample("a", 1_000L))
            repo.insert(sample("b", 2_000L))
            repo.insert(sample("c", 3_000L))
            repo.observeAll().test {
                val rows = awaitItem()
                // selectAll orders DESC by captured_at, so c=3, b=2, a=1
                assertEquals(listOf(3, 2, 1), rows.map { it.stampNumber })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun delete_then_insert_does_not_recycle_stamp_number() =
        runTest {
            val repo = newRepo()
            repo.insert(sample("a", 1_000L)) // #1
            repo.insert(sample("b", 2_000L)) // #2
            repo.delete("a")
            repo.insert(sample("c", 3_000L)) // #3 (not #2)
            repo.observeAllByStampNumber().test {
                val rows = awaitItem()
                assertEquals(listOf(3, 2), rows.map { it.stampNumber })
                cancelAndIgnoreRemainingEvents()
            }
        }
}
