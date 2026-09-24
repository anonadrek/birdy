package se.birdy.app.data.premium

import kotlinx.coroutines.test.runTest
import se.birdy.domain.premium.BillingUnavailableException
import se.birdy.domain.premium.PremiumState
import se.birdy.domain.premium.PremiumTier
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BillingPremiumRepositoryTest {
    @Test
    fun `initial state is Free`() =
        runTest {
            val fake = FakePremiumBillingClient()
            val repo = BillingPremiumRepository(fake.state, queryPurchases = fake.queryPurchases(true))
            assertIs<PremiumState.Free>(repo.state.value)
        }

    @Test
    fun `state flips to Active when billing emits Active`() =
        runTest {
            val fake = FakePremiumBillingClient()
            val repo = BillingPremiumRepository(fake.state, queryPurchases = fake.queryPurchases(true))
            fake.setActive(PremiumTier.YEARLY)
            kotlinx.coroutines.yield()
            assertIs<PremiumState.Active>(repo.state.value)
        }

    @Test
    fun `restore calls queryPurchases`() =
        runTest {
            val fake = FakePremiumBillingClient()
            val repo = BillingPremiumRepository(fake.state, queryPurchases = fake.queryPurchases(true))
            repo.restore()
            assertTrue(fake.queryPurchasesCalls == 1)
        }

    @Test
    fun `restore throws BillingUnavailableException when queryPurchases fails`() =
        runTest {
            val fake = FakePremiumBillingClient()
            val repo = BillingPremiumRepository(fake.state, queryPurchases = fake.queryPurchases(false))
            assertFailsWith<BillingUnavailableException> { repo.restore() }
        }
}
