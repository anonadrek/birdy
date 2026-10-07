package se.birdy.app.ui.settings

import app.cash.turbine.test
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.settings_restore_purchases_success
import birdy_bird_scanner.composeapp.generated.resources.settings_restore_purchases_unavailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import se.birdy.app.data.premium.PremiumProducts
import se.birdy.app.testing.FakePremiumRepository
import se.birdy.datastore.AppLanguage
import se.birdy.datastore.InMemoryUserPreferences
import se.birdy.domain.premium.BillingUnavailableException
import se.birdy.domain.premium.PremiumState
import se.birdy.domain.premium.PremiumTier
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @BeforeTest
    fun setMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun resetMain() = Dispatchers.resetMain()

    @Test
    fun `initial state reflects datastore`() =
        runTest {
            val prefs = InMemoryUserPreferences()
            prefs.setUserName("Albin")
            prefs.setAppLanguage(AppLanguage.SV)
            val vm = SettingsViewModel(prefs, FakePremiumRepository())
            vm.state.test {
                val s = awaitItem()
                assertEquals("Albin", s.userName)
                assertEquals(AppLanguage.SV, s.language)
                assertFalse(s.premiumActive)
            }
        }

    @Test
    fun `saveName updates datastore`() =
        runTest {
            val prefs = InMemoryUserPreferences()
            val vm = SettingsViewModel(prefs, FakePremiumRepository())
            vm.saveName("Bjorn")
            prefs.userName.test { assertEquals("Bjorn", awaitItem()) }
        }

    @Test
    fun `saveLanguage updates datastore`() =
        runTest {
            val prefs = InMemoryUserPreferences()
            val vm = SettingsViewModel(prefs, FakePremiumRepository())
            vm.saveLanguage(AppLanguage.EN)
            prefs.appLanguage.test { assertEquals(AppLanguage.EN, awaitItem()) }
        }

    @Test
    fun `premiumActive reflects repository state`() =
        runTest {
            val prefs = InMemoryUserPreferences()
            val premiumRepo = FakePremiumRepository(PremiumState.Active(PremiumTier.YEARLY, Clock.System.now()))
            val vm = SettingsViewModel(prefs, premiumRepo)
            vm.state.test {
                assertTrue(awaitItem().premiumActive)
            }
        }

    @Test
    fun `override active with free billing shows premium as active`() =
        runTest {
            val prefs = InMemoryUserPreferences()
            val premiumRepo = FakePremiumRepository(PremiumState.Free)
            val vm =
                SettingsViewModel(
                    prefs,
                    premiumRepo,
                    premiumOverride = PremiumState.Active(PremiumTier.LIFETIME, Clock.System.now()),
                )
            vm.state.test {
                assertTrue(awaitItem().premiumActive)
            }
        }

    @Test
    fun `restore with override active reports success`() =
        runTest {
            val prefs = InMemoryUserPreferences()
            val premiumRepo = FakePremiumRepository(PremiumState.Free)
            val vm =
                SettingsViewModel(
                    prefs,
                    premiumRepo,
                    premiumOverride = PremiumState.Active(PremiumTier.LIFETIME, Clock.System.now()),
                )
            vm.effects.test {
                vm.restorePurchases()
                assertEquals(SettingsEffect.ShowToast(Res.string.settings_restore_purchases_success), awaitItem())
            }
        }

    @Test
    fun `playSubscriptionTier is yearly for an active yearly billing subscription`() =
        runTest {
            val prefs = InMemoryUserPreferences()
            val premiumRepo = FakePremiumRepository(PremiumState.Active(PremiumTier.YEARLY, Clock.System.now()))
            val vm = SettingsViewModel(prefs, premiumRepo)
            vm.state.test {
                assertEquals(PremiumTier.YEARLY, awaitItem().playSubscriptionTier)
            }
        }

    @Test
    fun `playSubscriptionTier is lifetime for an active lifetime billing purchase`() =
        runTest {
            val prefs = InMemoryUserPreferences()
            val premiumRepo = FakePremiumRepository(PremiumState.Active(PremiumTier.LIFETIME, Clock.System.now()))
            val vm = SettingsViewModel(prefs, premiumRepo)
            vm.state.test {
                assertEquals(PremiumTier.LIFETIME, awaitItem().playSubscriptionTier)
            }
        }

    @Test
    fun `playSubscriptionTier is null for a free billing backend`() =
        runTest {
            val prefs = InMemoryUserPreferences()
            val vm = SettingsViewModel(prefs, FakePremiumRepository(PremiumState.Free))
            vm.state.test {
                assertEquals(null, awaitItem().playSubscriptionTier)
            }
        }

    @Test
    fun `a grandfathered lifetime override does not fabricate a tier over a free billing backend`() =
        runTest {
            val prefs = InMemoryUserPreferences()
            val premiumRepo = FakePremiumRepository(PremiumState.Free)
            val vm =
                SettingsViewModel(
                    prefs,
                    premiumRepo,
                    premiumOverride = PremiumState.Active(PremiumTier.LIFETIME, Clock.System.now()),
                )
            vm.state.test {
                val s = awaitItem()
                assertTrue(s.premiumActive) // Premium is on via the override...
                assertEquals(null, s.playSubscriptionTier) // ...but there is no Play subscription to manage.
            }
        }

    @Test
    fun `a grandfathered lifetime override still shows a real yearly billing subscription`() =
        runTest {
            // A grandfathered device whose Google account separately holds a real yearly
            // subscription must still surface the cancel link — the override must never hide it.
            val prefs = InMemoryUserPreferences()
            val premiumRepo = FakePremiumRepository(PremiumState.Active(PremiumTier.YEARLY, Clock.System.now()))
            val vm =
                SettingsViewModel(
                    prefs,
                    premiumRepo,
                    premiumOverride = PremiumState.Active(PremiumTier.LIFETIME, Clock.System.now()),
                )
            vm.state.test {
                assertEquals(PremiumTier.YEARLY, awaitItem().playSubscriptionTier)
            }
        }

    @Test
    fun `a debug force-yearly override does not fabricate a tier over a free billing backend`() =
        runTest {
            // The debug "force yearly" override grants Premium for testing with no real Play
            // purchase behind it — it must never make the row claim a subscription exists.
            val prefs = InMemoryUserPreferences()
            val premiumRepo = FakePremiumRepository(PremiumState.Free)
            val vm =
                SettingsViewModel(
                    prefs,
                    premiumRepo,
                    premiumOverride = PremiumState.Active(PremiumTier.YEARLY, Clock.System.now()),
                )
            vm.state.test {
                val s = awaitItem()
                assertTrue(s.premiumActive)
                assertEquals(null, s.playSubscriptionTier)
            }
        }

    @Test
    fun `openManageSubscription sends the manage subscription effect with the yearly sku`() =
        runTest {
            val prefs = InMemoryUserPreferences()
            val premiumRepo = FakePremiumRepository(PremiumState.Active(PremiumTier.YEARLY, Clock.System.now()))
            val vm = SettingsViewModel(prefs, premiumRepo)
            vm.effects.test {
                vm.openManageSubscription()
                assertEquals(SettingsEffect.OpenManageSubscriptionUrl(PremiumProducts.YEARLY), awaitItem())
            }
        }

    @Test
    fun `no override keeps billing state`() =
        runTest {
            val prefs = InMemoryUserPreferences()
            val premiumRepo = FakePremiumRepository(PremiumState.Free)
            val vm = SettingsViewModel(prefs, premiumRepo)
            vm.state.test {
                assertFalse(awaitItem().premiumActive)
            }
        }

    @Test
    fun `restore when play is unreachable reports unavailable`() =
        runTest {
            val prefs = InMemoryUserPreferences()
            val premiumRepo = FakePremiumRepository(PremiumState.Free, restoreThrows = BillingUnavailableException())
            val vm = SettingsViewModel(prefs, premiumRepo)
            vm.effects.test {
                vm.restorePurchases()
                assertEquals(
                    SettingsEffect.ShowToast(Res.string.settings_restore_purchases_unavailable),
                    awaitItem(),
                )
            }
        }

    @Test
    fun `restore when play is unreachable but override active reports success`() =
        runTest {
            val prefs = InMemoryUserPreferences()
            val premiumRepo = FakePremiumRepository(PremiumState.Free, restoreThrows = BillingUnavailableException())
            val vm =
                SettingsViewModel(
                    prefs,
                    premiumRepo,
                    premiumOverride = PremiumState.Active(PremiumTier.LIFETIME, Clock.System.now()),
                )
            vm.effects.test {
                vm.restorePurchases()
                assertEquals(SettingsEffect.ShowToast(Res.string.settings_restore_purchases_success), awaitItem())
            }
        }

    @Test
    fun `restore when play is unreachable and billing already active reports unavailable`() =
        runTest {
            val prefs = InMemoryUserPreferences()
            val premiumRepo =
                FakePremiumRepository(
                    PremiumState.Active(PremiumTier.YEARLY, Clock.System.now()),
                    restoreThrows = BillingUnavailableException(),
                )
            val vm = SettingsViewModel(prefs, premiumRepo)
            vm.effects.test {
                vm.restorePurchases()
                assertEquals(
                    SettingsEffect.ShowToast(Res.string.settings_restore_purchases_unavailable),
                    awaitItem(),
                )
            }
        }

    @Test
    fun `restore with unexpected error reports unavailable`() =
        runTest {
            val prefs = InMemoryUserPreferences()
            val premiumRepo = FakePremiumRepository(PremiumState.Free, restoreThrows = IllegalStateException("boom"))
            val vm = SettingsViewModel(prefs, premiumRepo)
            vm.effects.test {
                vm.restorePurchases()
                assertEquals(
                    SettingsEffect.ShowToast(Res.string.settings_restore_purchases_unavailable),
                    awaitItem(),
                )
            }
        }
}
