package se.birdy.data.dailybird

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import se.birdy.data.db.BirdyData
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DailyBirdHistoryRepositoryTest {
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var repo: DailyBirdHistoryRepository

    @BeforeTest
    fun setup() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        BirdyData.Schema.create(driver)
        repo = DailyBirdHistoryRepositoryImpl(BirdyData(driver))
    }

    @AfterTest
    fun tearDown() = driver.close()

    @Test
    fun `recordToday persists species for date`() =
        runTest {
            val date = LocalDate(2026, 5, 25)
            repo.recordToday(date, "Q25485")
            assertEquals("Q25485", repo.speciesIdForDate(date))
        }

    @Test
    fun `speciesIdForDate returns null when no row`() =
        runTest {
            assertNull(repo.speciesIdForDate(LocalDate(2026, 5, 25)))
        }

    @Test
    fun `recordToday is idempotent for same date`() =
        runTest {
            val date = LocalDate(2026, 5, 25)
            repo.recordToday(date, "Q25485")
            repo.recordToday(date, "DIFFERENT_ID")
            assertEquals("Q25485", repo.speciesIdForDate(date))
            assertEquals(0, repo.totalMatchCount())
        }

    @Test
    fun `markMatch increments counter only on matching species`() =
        runTest {
            repo.recordToday(LocalDate(2026, 5, 25), "Q25485")
            repo.recordToday(LocalDate(2026, 5, 26), "Q25485")
            repo.recordToday(LocalDate(2026, 5, 27), "Q12345")
            repo.markMatch(LocalDate(2026, 5, 25), "Q25485")
            repo.markMatch(LocalDate(2026, 5, 26), "Q25485")
            repo.markMatch(LocalDate(2026, 5, 27), "Q99999") // species mismatch — no-op
            assertEquals(2, repo.totalMatchCount())
        }
}
