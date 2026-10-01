package se.birdy.app.notifications

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import se.birdy.app.testing.FakeBadgeRepository
import se.birdy.app.testing.FakeObservationRepository
import se.birdy.app.testing.FakeUserPreferences
import se.birdy.domain.badge.BadgeCatalog
import se.birdy.domain.dailybird.DailyBird
import se.birdy.domain.observation.Observation
import se.birdy.domain.observation.ObservationRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * Null-path coverage only. The non-null paths call compose-resources `getString`,
 * which cannot run on Kotlin/Native test binaries (see trap catalog in CLAUDE.md) —
 * those are exercised by the pre-existing worker device-verify flow instead.
 *
 * The three `*_disabled_pref_returns_null` tests are deliberately built so the
 * pref-gate is the ONLY thing that can produce the null — each fixture is set up
 * so that if the gate were deleted, the function would fall through to a different,
 * detectable code path instead of a second, unrelated null (see per-test doc).
 * These gates are user-facing consent toggles on a shipping app.
 */
class NotificationPayloadsTest {
    private val date = LocalDate(2026, 5, 30)

    private suspend fun build(
        dailyEnabled: Boolean = false,
        recapEnabled: Boolean = false,
        trophyEnabled: Boolean = false,
        selector: (suspend (LocalDate) -> DailyBird?)? = null,
        observationRepo: ObservationRepository = FakeObservationRepository(),
        badgeCatalog: BadgeCatalog = BadgeCatalog(version = 1, badges = emptyList()),
    ): NotificationPayloads {
        val prefs = FakeUserPreferences()
        prefs.setDailyBirdPushEnabled(dailyEnabled)
        prefs.setWeeklyRecapPushEnabled(recapEnabled)
        prefs.setWeeklyTrophyPushEnabled(trophyEnabled)
        return NotificationPayloads(
            prefs = prefs,
            observationRepo = observationRepo,
            badgeRepo = FakeBadgeRepository(),
            badgeCatalog = badgeCatalog,
            speciesByQid = { emptyMap() },
            speciesNameFor = { null },
            selectDailyBird = selector,
            dailyBirdMatchCount = { 0 },
            timeZone = TimeZone.of("Europe/Stockholm"),
            clock = Clock.System,
        )
    }

    /**
     * Mutation-killing for the gate at `dailyBird()`'s first line: the helper default
     * (`selector = null`) would make the null over-determined (a null selector also
     * returns null), so this passes a call-counting non-null selector instead — a
     * deleted gate is caught by the selector having been invoked, not by the return
     * value alone.
     */
    @Test
    fun dailyBird_disabled_pref_returns_null() =
        runTest {
            var selectorCalls = 0
            val content =
                build(
                    dailyEnabled = false,
                    selector = {
                        selectorCalls++
                        null
                    },
                ).dailyBird(date)
            assertNull(content)
            assertEquals(0, selectorCalls)
        }

    @Test
    fun dailyBird_null_selector_returns_null() = runTest { assertNull(build(dailyEnabled = true, selector = null).dailyBird(date)) }

    /**
     * Mutation-killing for the gate at `weeklyRecap()`'s first line: an empty
     * observation repo would make the null over-determined (a quiet week is
     * ALSO null), so this seeds one observation captured "now" — always this
     * week, whatever day the suite runs on — so a deleted gate falls through to
     * the non-quiet/active branch (a non-null `getString`-built result, or a
     * `getString` failure on the JVM test target — either way not the expected
     * null) instead of the natural quiet-week null.
     */
    @Test
    fun weeklyRecap_disabled_pref_returns_null() =
        runTest {
            val obsRepo = FakeObservationRepository()
            obsRepo.seedObservation(speciesId = "Q25485", capturedAt = Clock.System.now())
            assertNull(build(recapEnabled = false, observationRepo = obsRepo).weeklyRecap())
        }

    @Test
    fun weeklyRecap_quiet_week_no_streak_returns_null() = runTest { assertNull(build(recapEnabled = true).weeklyRecap()) }

    /**
     * Mutation-killing for the gate at `trophyProgress()`'s first line: an empty
     * badge catalog would make the null over-determined (`TrophyProgress` yields no
     * closest badge regardless of the gate), so this instead tracks whether
     * `observeAll()` — the first collaborator call after the gate — was ever
     * reached. The gate returning early is the only way to keep it `false`.
     */
    @Test
    fun trophy_disabled_pref_returns_null() =
        runTest {
            val obsRepo = ObserveAllTrackingRepository()
            val content = build(trophyEnabled = false, observationRepo = obsRepo).trophyProgress()
            assertNull(content)
            assertFalse(obsRepo.observeAllCalled)
        }

    @Test
    fun trophy_nothing_in_progress_returns_null() = runTest { assertNull(build(trophyEnabled = true).trophyProgress()) }

    @Test
    fun fromGraphOr_null_graph_uses_standalone() =
        runTest {
            val standalone = build()
            var standaloneCalls = 0
            val result =
                NotificationPayloads.fromGraphOr(graph = null) {
                    standaloneCalls++
                    standalone
                }
            assertSame(standalone, result)
            assertEquals(1, standaloneCalls)
        }
}

/** Wraps [ObservationRepository] to detect whether code reached past a pref-gate. */
private class ObserveAllTrackingRepository(
    private val delegate: ObservationRepository = FakeObservationRepository(),
) : ObservationRepository by delegate {
    var observeAllCalled: Boolean = false
        private set

    override fun observeAll(): Flow<List<Observation>> {
        observeAllCalled = true
        return delegate.observeAll()
    }
}
