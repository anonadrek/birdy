package se.birdy.data.observation

import app.cash.sqldelight.db.SqlDriver
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.junit.jupiter.api.AfterEach
import se.birdy.data.DatabaseFactory
import se.birdy.data.db.BirdyData
import se.birdy.domain.observation.Observation
import se.birdy.domain.observation.ObservationSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ObservationRepositoryAudioTest {
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

    private fun audioObs(
        id: String,
        audioPath: String? = "/tmp/clip.opus",
    ) = Observation(
        id = id,
        speciesId = "Q25334",
        capturedAt = Clock.System.now(),
        savedAt = Clock.System.now(),
        photoPath = "/tmp/wave.png",
        note = "",
        confidence = 0.9f,
        latitude = null,
        longitude = null,
        locationLabel = null,
        audioPath = audioPath,
        sourceType = ObservationSource.Audio,
    )

    @Test
    fun insertAudioObs_persistsAudioPathAndSource() =
        runTest {
            val repo = newRepo()
            repo.insert(audioObs("a1"))
            repo.observeById("a1").test {
                val loaded = awaitItem()
                assertEquals("/tmp/clip.opus", loaded?.audioPath)
                assertEquals(ObservationSource.Audio, loaded?.sourceType)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun delete_returnsFileCleanupRequestWithBothPaths() =
        runTest {
            val repo = newRepo()
            repo.insert(audioObs("a2"))
            val cleanup = repo.delete("a2")
            assertEquals("/tmp/wave.png", cleanup.photoPath)
            assertEquals("/tmp/clip.opus", cleanup.audioPath)
        }

    @Test
    fun existingPhotoObs_defaultsToPhotoSource() =
        runTest {
            val repo = newRepo()
            repo.insert(
                Observation(
                    id = "p1",
                    speciesId = "Q25334",
                    capturedAt = Clock.System.now(),
                    savedAt = Clock.System.now(),
                    photoPath = "/tmp/foto.jpg",
                    note = "",
                    confidence = 0.9f,
                    latitude = null,
                    longitude = null,
                    locationLabel = null,
                ),
            )
            repo.observeById("p1").test {
                val loaded = awaitItem()
                assertEquals(ObservationSource.Photo, loaded?.sourceType)
                assertNull(loaded?.audioPath)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
