package se.birdy.app.testing

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FakeObservationRepositoryTest {
    @Test
    fun countByQid_returns_zero_when_empty() =
        runTest {
            val repo = FakeObservationRepository()
            assertEquals(0, repo.countByQid("Q25485"))
        }

    @Test
    fun countByQid_returns_match_count_for_species() =
        runTest {
            val repo = FakeObservationRepository()
            repo.seedObservation("Q25485", Instant.parse("2026-05-01T10:00:00Z"), id = "a")
            repo.seedObservation("Q25485", Instant.parse("2026-05-03T10:00:00Z"), id = "b")
            repo.seedObservation("Q99999", Instant.parse("2026-05-02T10:00:00Z"), id = "c")
            assertEquals(2, repo.countByQid("Q25485"))
            assertEquals(1, repo.countByQid("Q99999"))
            assertEquals(0, repo.countByQid("Q_OTHER"))
        }

    @Test
    fun firstByQid_returns_null_when_no_match() =
        runTest {
            val repo = FakeObservationRepository()
            assertNull(repo.firstByQid("Q25485"))
        }

    @Test
    fun firstByQid_returns_earliest_captured_at_for_species() =
        runTest {
            val repo = FakeObservationRepository()
            val later = Instant.parse("2026-05-05T10:00:00Z")
            val earlier = Instant.parse("2026-04-03T10:00:00Z")
            repo.seedObservation("Q25485", later, id = "later")
            repo.seedObservation("Q25485", earlier, id = "earlier")
            assertEquals(earlier, repo.firstByQid("Q25485"))
        }
}
