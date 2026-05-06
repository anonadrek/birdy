package se.birdy.app.ui.badges

import app.cash.turbine.test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import se.birdy.domain.badge.BadgeUnlock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UnlockQueueTest {
    @Test
    fun `current is null when queue is empty`() =
        runTest {
            val q = UnlockQueue()
            assertNull(q.current.first())
            assertEquals(0, q.size.first())
        }

    @Test
    fun `enqueue then current emits first unlock`() =
        runTest {
            val q = UnlockQueue()
            val u1 = BadgeUnlock("a", Instant.fromEpochMilliseconds(1L))
            val u2 = BadgeUnlock("b", Instant.fromEpochMilliseconds(2L))
            q.enqueue(listOf(u1, u2))

            assertEquals(u1, q.current.first())
            assertEquals(2, q.size.first())
        }

    @Test
    fun `pop advances to next`() =
        runTest {
            val q = UnlockQueue()
            val u1 = BadgeUnlock("a", Instant.fromEpochMilliseconds(1L))
            val u2 = BadgeUnlock("b", Instant.fromEpochMilliseconds(2L))
            q.enqueue(listOf(u1, u2))
            q.pop()
            assertEquals(u2, q.current.first())
            assertEquals(1, q.size.first())
        }

    @Test
    fun `pop on empty queue is no-op`() =
        runTest {
            val q = UnlockQueue()
            q.pop()
            assertNull(q.current.first())
        }

    @Test
    fun `enqueue empty list is no-op`() =
        runTest {
            val q = UnlockQueue()
            q.enqueue(emptyList())
            assertNull(q.current.first())
        }

    @Test
    fun `enqueue concatenates onto existing queue`() =
        runTest {
            val q = UnlockQueue()
            q.enqueue(listOf(BadgeUnlock("a", Instant.fromEpochMilliseconds(1L))))
            q.enqueue(listOf(BadgeUnlock("b", Instant.fromEpochMilliseconds(2L))))
            assertEquals(2, q.size.first())
            assertEquals("a", q.current.first()?.badgeId)
            q.pop()
            assertEquals("b", q.current.first()?.badgeId)
        }

    @Test
    fun `current emits new value when pop changes head`() =
        runTest {
            val q = UnlockQueue()
            q.enqueue(
                listOf(
                    BadgeUnlock("a", Instant.fromEpochMilliseconds(1L)),
                    BadgeUnlock("b", Instant.fromEpochMilliseconds(2L)),
                ),
            )
            q.current.test {
                assertEquals("a", awaitItem()?.badgeId)
                q.pop()
                assertEquals("b", awaitItem()?.badgeId)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
