package se.birdy.data.badge

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import se.birdy.data.db.BirdyData
import se.birdy.domain.badge.BadgeUnlock
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class BadgeRepositoryImplTest {
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var db: BirdyData
    private lateinit var repo: BadgeRepositoryImpl

    @BeforeTest
    fun setup() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        BirdyData.Schema.create(driver)
        db = BirdyData(driver)
        repo = BadgeRepositoryImpl(db.badgeUnlockQueries)
    }

    @AfterTest
    fun tearDown() = driver.close()

    @Test
    fun `persist then observe emits inserted unlock`() =
        runTest {
            val unlock = BadgeUnlock("novice", Instant.fromEpochMilliseconds(1_700_000_000_000))
            repo.persist(listOf(unlock))

            repo.observeUnlocks().test {
                val emitted = awaitItem()
                assertEquals(1, emitted.size)
                assertEquals("novice", emitted[0].badgeId)
                assertEquals(unlock.unlockedAt, emitted[0].unlockedAt)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `persist is idempotent (upsert)`() =
        runTest {
            val first = BadgeUnlock("novice", Instant.fromEpochMilliseconds(1_700_000_000_000))
            val second = BadgeUnlock("novice", Instant.fromEpochMilliseconds(1_700_000_009_999))
            repo.persist(listOf(first))
            repo.persist(listOf(second))

            repo.observeUnlocks().test {
                val emitted = awaitItem()
                assertEquals(1, emitted.size)
                assertEquals(second.unlockedAt, emitted[0].unlockedAt)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `observeUnlocks sorts DESC by unlockedAt`() =
        runTest {
            val older = BadgeUnlock("novice", Instant.fromEpochMilliseconds(1_700_000_000_000))
            val newer = BadgeUnlock("birder_bronze", Instant.fromEpochMilliseconds(1_700_000_999_999))
            repo.persist(listOf(older, newer))

            repo.observeUnlocks().test {
                val emitted = awaitItem()
                assertEquals("birder_bronze", emitted[0].badgeId)
                assertEquals("novice", emitted[1].badgeId)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `deleteAll clears table`() =
        runTest {
            repo.persist(listOf(BadgeUnlock("novice", Instant.fromEpochMilliseconds(1L))))
            repo.deleteAll()

            repo.observeUnlocks().test {
                assertEquals(emptyList(), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
}
