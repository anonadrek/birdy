package se.birdy.app.ui.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import se.birdy.app.testing.FakePremiumRepository
import se.birdy.app.testing.FakeUserPreferences
import se.birdy.datastore.AppLanguage
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelLanguageEffectTest {
    @BeforeTest
    fun setup() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun teardown() = Dispatchers.resetMain()

    @Test
    fun saveLanguage_sv_emits_restart_for_locale_sv() =
        runTest {
            val prefs = FakeUserPreferences()
            val vm = SettingsViewModel(prefs, FakePremiumRepository())
            vm.saveLanguage(AppLanguage.SV)
            val effect = vm.effects.first()
            assertEquals(SettingsEffect.ApplyLocale("sv"), effect)
        }

    @Test
    fun saveLanguage_en_emits_restart_for_locale_en() =
        runTest {
            val prefs = FakeUserPreferences()
            val vm = SettingsViewModel(prefs, FakePremiumRepository())
            vm.saveLanguage(AppLanguage.EN)
            assertEquals(SettingsEffect.ApplyLocale("en"), vm.effects.first())
        }

    @Test
    fun saveLanguage_system_emits_restart_with_empty_tag() =
        runTest {
            val prefs = FakeUserPreferences()
            val vm = SettingsViewModel(prefs, FakePremiumRepository())
            vm.saveLanguage(AppLanguage.SYSTEM)
            assertEquals(SettingsEffect.ApplyLocale(""), vm.effects.first())
        }
}
