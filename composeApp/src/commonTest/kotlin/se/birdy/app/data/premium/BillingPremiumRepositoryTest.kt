package se.birdy.app.data.premium

import kotlinx.coroutines.test.runTest
import se.birdy.domain.premium.PremiumState
import se.birdy.domain.premium.PremiumTier
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BillingPremiumRepositoryTest {
    @Test
    fun `initial state is Free`() =
        runTest {
            val fake = FakePremiumBillingClient()
            val repo = BillingPremiumRepository(fake.state, queryPurchases = { fake.purchasesQueried++ })
            assertIs<PremiumState.Free>(repo.state.value)
        }

    @Test
    fun `state flips to Active when billing emits Active`() =
        runTest {
            val fake = FakePremiumBillingClient()
            val repo = BillingPremiumRepository(fake.state, queryPurchases = { fake.purchasesQueried++ })
            fake.setActive(PremiumTier.YEARLY)
            kotlinx.coroutines.yield()
            assertIs<PremiumState.Active>(repo.state.value)
        }

    @Test
    fun `restore calls queryPurchases`() =
        runTest {
            val fake = FakePremiumBillingClient()
            val repo = BillingPremiumRepository(fake.state, queryPurchases = { fake.purchasesQueried++ })
            repo.restore()
            assertTrue(fake.purchasesQueried == 1)
        }
}
