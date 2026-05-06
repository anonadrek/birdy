package se.birdy.app.usecase

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import se.birdy.app.testing.FakeClock
import se.birdy.app.testing.FakeObservationRepository
import se.birdy.app.testing.FakePhotoStorage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

class SaveObservationUseCaseTest {
    private val capturedAt = Instant.parse("2026-05-06T10:00:00Z")
    private val clock = FakeClock(now = Instant.parse("2026-05-06T10:00:30Z"))
    private val sampleBytes = ByteArray(8) { 0x42 }

    @Test
    fun success_inserts_row_and_persists_photo() =
        runTest {
            val repo = FakeObservationRepository()
            val storage = FakePhotoStorage()
            val useCase = SaveObservationUseCase(repo, storage, clock)
            val id =
                useCase.save(
                    speciesId = "Q25485",
                    capturedAt = capturedAt,
                    confidence = 0.87f,
                    rawJpegBytes = sampleBytes,
                    note = "vid mataren",
                )
            assertTrue(id.isNotBlank())
            val rows = repo.observeAll().first()
            assertEquals(1, rows.size)
            assertEquals(id, rows[0].id)
            assertEquals("vid mataren", rows[0].note)
            assertEquals(1, storage.persisted.size)
        }

    @Test
    fun saved_at_uses_clock() =
        runTest {
            val repo = FakeObservationRepository()
            val storage = FakePhotoStorage()
            val useCase = SaveObservationUseCase(repo, storage, clock)
            useCase.save("Q25485", capturedAt, 0.5f, sampleBytes, "")
            val row = repo.observeAll().first().single()
            assertEquals(clock.now, row.savedAt)
            assertEquals(capturedAt, row.capturedAt)
        }

    @Test
    fun photo_fail_does_not_insert_row() =
        runTest {
            val repo = FakeObservationRepository()
            val storage = FakePhotoStorage().apply { failOnPersist = RuntimeException("boom") }
            val useCase = SaveObservationUseCase(repo, storage, clock)
            assertFails {
                useCase.save("Q25485", capturedAt, 0.5f, sampleBytes, "")
            }
            assertEquals(emptyList(), repo.observeAll().first())
            assertEquals(0, storage.persisted.size)
        }

    @Test
    fun db_fail_after_photo_cleans_up_photo() =
        runTest {
            val repo = FakeObservationRepository().apply { failOnInsert = RuntimeException("db boom") }
            val storage = FakePhotoStorage()
            val useCase = SaveObservationUseCase(repo, storage, clock)
            assertFails {
                useCase.save("Q25485", capturedAt, 0.5f, sampleBytes, "")
            }
            assertEquals(0, storage.persisted.size)
        }

    @Test
    fun delete_failure_during_cleanup_is_swallowed() =
        runTest {
            val repo = FakeObservationRepository().apply { failOnInsert = RuntimeException("db boom") }
            val storage = FakePhotoStorage().apply { deleteThrows = true }
            val useCase = SaveObservationUseCase(repo, storage, clock)
            // The DB-failure still propagates; the photo-delete-failure is silently caught.
            val ex = assertFails { useCase.save("Q25485", capturedAt, 0.5f, sampleBytes, "") }
            assertEquals("db boom", ex.message)
        }
}
