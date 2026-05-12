package se.birdy.datastore

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import se.birdy.domain.premium.PremiumState
import se.birdy.domain.premium.PremiumTier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DataStorePremiumRepositoryTest {
    @Test
    fun `initial state is Free`() =
        runTest {
            val repo = PremiumStateStore(platformContext = null).repository()
            assertEquals(PremiumState.Free, repo.state.first())
        }

    @Test
    fun `markPurchased YEARLY emits Active YEARLY`() =
        runTest {
            val repo = PremiumStateStore(platformContext = null).repository()
            val before = Clock.System.now()
            repo.markPurchased(PremiumTier.YEARLY)
            val s = repo.state.first()
            assertIs<PremiumState.Active>(s)
            assertEquals(PremiumTier.YEARLY, s.tier)
            assertTrue(s.purchasedAt >= before, "purchasedAt should be >= test start time")
        }

    @Test
    fun `markPurchased LIFETIME emits Active LIFETIME`() =
        runTest {
            val repo = PremiumStateStore(platformContext = null).repository()
            repo.markPurchased(PremiumTier.LIFETIME)
            val s = repo.state.first()
            assertIs<PremiumState.Active>(s)
            assertEquals(PremiumTier.LIFETIME, s.tier)
        }

    @Test
    fun `forceState Free resets after Active`() =
        runTest {
            val store = PremiumStateStore(platformContext = null)
            val repo = store.repository()
            repo.markPurchased(PremiumTier.YEARLY)
            store.debugOverrides().forceState(PremiumState.Free)
            assertEquals(PremiumState.Free, repo.state.first())
        }
}
