package se.birdy.app.dailybird

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import se.birdy.data.dailybird.DailyBirdHistoryRepository
import se.birdy.datastore.UserPreferences
import se.birdy.domain.dailybird.DailyBird
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Days the daily bird must be caught for the Dagens fågel-jägare badge (premium_badges.yaml). */
const val DAILY_BIRD_HUNT_TARGET = 3

/** What the daily-bird surfaces need to know about a species. */
data class DailyBirdSpecies(
    val name: String,
    val scientificName: String,
    val heroImagePath: String?,
)

/**
 * Today's bird as every daily-bird surface shows it: the Identify hero, the strips on Mina arter
 * and Uppslagsverk, and (through [DailyBirdTracker.showTabDot]) the dot on the Identify tab.
 *
 * [daysCaught] is the number of distinct days on which the user saved that day's bird (all time,
 * not days in a row), the same count the Dagens fågel-jägare badge rule reads.
 */
data class DailyBirdToday(
    val date: LocalDate,
    val speciesId: String,
    val name: String,
    val scientificName: String,
    val heroImagePath: String?,
    val caughtToday: Boolean,
    val daysCaught: Int,
    val huntTarget: Int = DAILY_BIRD_HUNT_TARGET,
)

/** The Identify tab's dot: shown while today's bird exists and has not been opened today. */
fun isDailyBirdDotVisible(
    bird: DailyBirdToday?,
    openedDate: String?,
): Boolean = bird != null && openedDate != bird.date.toString()

/** Time from [now] to the next local midnight in [zone] (23 or 25 hours on a daylight-saving day). */
fun untilNextLocalMidnight(
    now: Instant,
    zone: TimeZone,
): Duration {
    val tomorrow = now.toLocalDateTime(zone).date.plus(1, DateTimeUnit.DAY)
    return tomorrow.atStartOfDayIn(zone) - now
}

// Wake a little after midnight, so the new date is certain when the refresh reads the clock.
private val MidnightMargin = 1.seconds

/**
 * One shared source for today's bird (release 1.3.0 Task 7d), owned by the AppGraph so the hero,
 * both strips and the tab dot always agree.
 *
 * - [refreshNowAndAtMidnight] runs while the app is visible (AppScaffold, from ON_START to
 *   ON_STOP): a refresh at once, then one just after every local midnight, so an app left open
 *   overnight moves on to the new day's bird (and brings the dot back).
 * - [onSaved] runs after every save: if the state is from an earlier day it refreshes first,
 *   which records the new day's bird, then marks the catch when the saved species is today's bird.
 * - [onSpeciesOpened] runs when any species profile opens: opening today's bird stores today's
 *   date, which hides the tab dot until tomorrow.
 *
 * A refresh also records today's bird in the history; that is what lets a save of that species
 * count as a catch (it used to happen only when the Identify screen was composed).
 */
class DailyBirdTracker(
    private val select: (suspend (LocalDate) -> DailyBird?)?,
    private val species: suspend (speciesId: String) -> DailyBirdSpecies?,
    private val history: DailyBirdHistoryRepository?,
    private val prefs: UserPreferences,
    private val now: () -> Instant,
    private val timeZone: TimeZone,
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow<DailyBirdToday?>(null)
    val state: StateFlow<DailyBirdToday?> = _state.asStateFlow()

    val showTabDot: Flow<Boolean> =
        combine(state, prefs.dailyBirdOpenedDate) { bird, opened -> isDailyBirdDotVisible(bird, opened) }

    /** The local date the daily bird belongs to right now. */
    fun today(): LocalDate = now().toLocalDateTime(timeZone).date

    /**
     * Reloads today's bird. Never throws (except cancellation): on a database error the previous
     * state stays and the cause is logged, so a broken history can't take the Identify tab down
     * (any DB or selector failure degrades the same way, hence the generic catch).
     */
    @Suppress("TooGenericExceptionCaught")
    suspend fun refresh() {
        mutex.withLock {
            try {
                val date = today()
                _state.value = reread(date) ?: load(date)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                println("DailyBirdTracker: refresh failed, keeping the previous state: ${t.message}")
            }
        }
    }

    /** A refresh now, then one just after every local midnight, until cancelled. */
    suspend fun refreshNowAndAtMidnight() {
        while (true) {
            refresh()
            delay(untilNextLocalMidnight(now(), timeZone) + MidnightMargin)
        }
    }

    /** After a save: counts it as today's catch when [speciesId] is today's bird. */
    suspend fun onSaved(speciesId: String) {
        val date = today()
        // Saved after midnight with yesterday's state: today's bird isn't recorded yet, and a
        // catch can only be marked against a recorded day.
        if (_state.value?.date != date) refresh()
        val history = history ?: return
        if (history.speciesIdForDate(date) == speciesId) {
            history.markMatch(date, speciesId)
            // The hero, the strips and the challenge row show "Fångad idag" right away.
            refresh()
        }
    }

    suspend fun onSpeciesOpened(speciesId: String) {
        val date = today()
        val todaysBird =
            _state.value?.takeIf { it.date == date }?.speciesId
                // Not loaded yet (a notification tap can open the profile before the start-up
                // refresh is done) or loaded on an earlier date: ask the selector, which is
                // deterministic per date.
                ?: select?.invoke(date)?.speciesId
                ?: return
        if (todaysBird == speciesId) prefs.setDailyBirdOpenedDate(date.toString())
    }

    /**
     * Same day as the current state: only the catch can have changed, so re-read that and skip the
     * selector (on Android it loads all 839 species) and the species lookup. Null = load afresh.
     */
    private suspend fun reread(date: LocalDate): DailyBirdToday? =
        _state.value?.takeIf { it.date == date }?.copy(
            caughtToday = history?.isMatched(date) ?: false,
            daysCaught = history?.totalMatchCount() ?: 0,
        )

    private suspend fun load(date: LocalDate): DailyBirdToday? {
        val bird = select?.invoke(date)
        val info = bird?.let { species(it.speciesId) }
        if (bird == null || info == null) return null
        history?.recordToday(date, bird.speciesId)
        return DailyBirdToday(
            date = date,
            speciesId = bird.speciesId,
            name = info.name,
            scientificName = info.scientificName,
            heroImagePath = info.heroImagePath,
            caughtToday = history?.isMatched(date) ?: false,
            daysCaught = history?.totalMatchCount() ?: 0,
            huntTarget = DAILY_BIRD_HUNT_TARGET,
        )
    }
}
