package se.birdy.app.dailybird

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDate
import se.birdy.data.dailybird.DailyBirdHistoryRepository
import se.birdy.datastore.UserPreferences
import se.birdy.domain.dailybird.DailyBird

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

/**
 * One shared source for today's bird (release 1.3.0 Task 7d), owned by the AppGraph so the hero,
 * both strips and the tab dot always agree.
 *
 * [refresh] runs at every app start/foreground (AppScaffold) and after every save (AppGraph), so
 * a new date or a catch shows up without restarting. It also records today's bird in the history,
 * which is what lets a save of that species count as a catch (it used to happen only when the
 * Identify screen was composed). [onSpeciesOpened] is called by every species profile; it stores
 * today's date when the opened species is today's bird, which hides the tab dot until tomorrow.
 */
class DailyBirdTracker(
    private val select: (suspend (LocalDate) -> DailyBird?)?,
    private val species: suspend (speciesId: String) -> DailyBirdSpecies?,
    private val history: DailyBirdHistoryRepository?,
    private val prefs: UserPreferences,
    private val currentDate: () -> LocalDate,
    private val huntTarget: Int = DAILY_BIRD_HUNT_TARGET,
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow<DailyBirdToday?>(null)
    val state: StateFlow<DailyBirdToday?> = _state.asStateFlow()

    val showTabDot: Flow<Boolean> =
        combine(state, prefs.dailyBirdOpenedDate) { bird, opened -> isDailyBirdDotVisible(bird, opened) }

    /** The local date the daily bird belongs to right now. */
    fun today(): LocalDate = currentDate()

    /**
     * Reloads today's bird. Never throws (except cancellation): on a database error the previous
     * state stays and the cause is logged, so a broken history can't take the Identify tab down
     * (any DB or selector failure degrades the same way, hence the generic catch).
     */
    @Suppress("TooGenericExceptionCaught")
    suspend fun refresh() {
        mutex.withLock {
            try {
                _state.value = load(today())
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                println("DailyBirdTracker: refresh failed, keeping the previous state: ${t.message}")
            }
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
            huntTarget = huntTarget,
        )
    }
}
