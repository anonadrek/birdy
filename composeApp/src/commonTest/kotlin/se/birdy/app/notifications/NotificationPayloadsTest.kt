package se.birdy.app.notifications

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import se.birdy.app.testing.FakeBadgeRepository
import se.birdy.app.testing.FakeObservationRepository
import se.birdy.app.testing.FakeUserPreferences
import se.birdy.domain.badge.BadgeCatalog
import se.birdy.domain.dailybird.DailyBird
import kotlin.test.Test
import kotlin.test.assertNull

/**
 * Null-path coverage only. The non-null paths call compose-resources `getString`,
 * which cannot run on Kotlin/Native test binaries (see trap catalog in CLAUDE.md) —
 * those are exercised by the pre-existing worker device-verify flow instead.
 */
class NotificationPayloadsTest {
    private val date = LocalDate(2026, 5, 30)

    private suspend fun build(
        dailyEnabled: Boolean = false,
        recapEnabled: Boolean = false,
        trophyEnabled: Boolean = false,
        selector: (suspend (LocalDate) -> DailyBird?)? = null,
    ): NotificationPayloads {
        val prefs = FakeUserPreferences()
        prefs.setDailyBirdPushEnabled(dailyEnabled)
        prefs.setWeeklyRecapPushEnabled(recapEnabled)
        prefs.setWeeklyTrophyPushEnabled(trophyEnabled)
        return NotificationPayloads(
            prefs = prefs,
            observationRepo = FakeObservationRepository(),
            badgeRepo = FakeBadgeRepository(),
            badgeCatalog = BadgeCatalog(version = 1, badges = emptyList()),
            speciesByQid = { emptyMap() },
            speciesNameFor = { null },
            selectDailyBird = selector,
            dailyBirdMatchCount = { 0 },
            timeZone = TimeZone.of("Europe/Stockholm"),
            clock = Clock.System,
        )
    }

    @Test
    fun dailyBird_disabled_pref_returns_null() = runTest { assertNull(build(dailyEnabled = false).dailyBird(date)) }

    @Test
    fun dailyBird_null_selector_returns_null() = runTest { assertNull(build(dailyEnabled = true, selector = null).dailyBird(date)) }

    @Test
    fun weeklyRecap_disabled_pref_returns_null() = runTest { assertNull(build(recapEnabled = false).weeklyRecap()) }

    @Test
    fun weeklyRecap_quiet_week_no_streak_returns_null() = runTest { assertNull(build(recapEnabled = true).weeklyRecap()) }

    @Test
    fun trophy_disabled_pref_returns_null() = runTest { assertNull(build(trophyEnabled = false).trophyProgress()) }

    @Test
    fun trophy_nothing_in_progress_returns_null() = runTest { assertNull(build(trophyEnabled = true).trophyProgress()) }
}
