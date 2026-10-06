package se.birdy.app.dailybird

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import se.birdy.app.testing.FakeDailyBirdHistoryRepository
import se.birdy.app.testing.FakeUserPreferences
import se.birdy.domain.dailybird.DailyBird
import se.birdy.domain.dailybird.SeasonTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DailyBirdTrackerTest {
    private val tuesday = LocalDate(2026, 10, 6)
    private val wednesday = LocalDate(2026, 10, 7)

    private val sedgeWarbler = DailyBirdSpecies("Sävsångare", "Acrocephalus schoenobaenus", "Q25403/hero.webp")
    private val greatTit = DailyBirdSpecies("Talgoxe", "Parus major", null)

    private var today = tuesday
    private val history = FakeDailyBirdHistoryRepository()
    private val prefs = FakeUserPreferences()

    /** Tuesday's bird is the sedge warbler, Wednesday's the great tit. */
    private fun birdFor(date: LocalDate): String = if (date == tuesday) "Q25403" else "Q25485"

    private fun tracker(
        select: (suspend (LocalDate) -> DailyBird?)? = { date -> DailyBird(birdFor(date), SeasonTag.PRESENT) },
        species: suspend (String) -> DailyBirdSpecies? = { id -> if (id == "Q25403") sedgeWarbler else greatTit },
    ) = DailyBirdTracker(
        select = select,
        species = species,
        history = history,
        prefs = prefs,
        currentDate = { today },
    )

    @Test
    fun `refresh loads todays bird with its name scientific name and photo`() =
        runTest {
            val tracker = tracker()
            tracker.refresh()
            assertEquals(
                DailyBirdToday(
                    date = tuesday,
                    speciesId = "Q25403",
                    name = "Sävsångare",
                    scientificName = "Acrocephalus schoenobaenus",
                    heroImagePath = "Q25403/hero.webp",
                    caughtToday = false,
                    daysCaught = 0,
                    huntTarget = 3,
                ),
                tracker.state.value,
            )
        }

    @Test
    fun `refresh records todays bird so a save of that species can count as a catch`() =
        runTest {
            tracker().refresh()
            assertEquals("Q25403", history.recorded[tuesday])
        }

    @Test
    fun `no candidate for the day means no daily bird`() =
        runTest {
            val tracker = tracker(select = { null })
            tracker.refresh()
            assertNull(tracker.state.value)
            assertFalse(tracker.showTabDot.first())
        }

    @Test
    fun `a species missing from the database means no daily bird`() =
        runTest {
            val tracker = tracker(species = { null })
            tracker.refresh()
            assertNull(tracker.state.value)
        }

    @Test
    fun `caught today and the day count come from the history`() =
        runTest {
            history.recorded[LocalDate(2026, 9, 1)] = "Q1"
            history.matched += LocalDate(2026, 9, 1)
            val tracker = tracker()
            tracker.refresh()
            assertFalse(tracker.state.value!!.caughtToday)
            assertEquals(1, tracker.state.value!!.daysCaught)

            history.markMatch(tuesday, "Q25403")
            tracker.refresh()
            assertTrue(tracker.state.value!!.caughtToday)
            assertEquals(2, tracker.state.value!!.daysCaught)
        }

    @Test
    fun `the tab dot shows until todays bird is opened`() =
        runTest {
            val tracker = tracker()
            tracker.refresh()
            assertTrue(tracker.showTabDot.first())

            tracker.onSpeciesOpened("Q25403")
            assertFalse(tracker.showTabDot.first())
            assertEquals("2026-10-06", prefs.dailyBirdOpenedDate.first())
        }

    @Test
    fun `opening another species keeps the dot`() =
        runTest {
            val tracker = tracker()
            tracker.refresh()
            tracker.onSpeciesOpened("Q25485")
            assertTrue(tracker.showTabDot.first())
            assertNull(prefs.dailyBirdOpenedDate.first())
        }

    @Test
    fun `the dot comes back when the date changes`() =
        runTest {
            val tracker = tracker()
            tracker.refresh()
            tracker.onSpeciesOpened("Q25403")
            assertFalse(tracker.showTabDot.first())

            today = wednesday
            tracker.refresh()
            assertEquals(wednesday, tracker.state.value!!.date)
            assertEquals("Q25485", tracker.state.value!!.speciesId)
            assertTrue(tracker.showTabDot.first())
        }

    @Test
    fun `opening yesterdays bird after midnight does not clear the new days dot`() =
        runTest {
            val tracker = tracker()
            tracker.refresh()
            today = wednesday
            // The app was left open over midnight: state still holds Tuesday's bird.
            tracker.onSpeciesOpened("Q25403")
            assertNull(prefs.dailyBirdOpenedDate.first())
        }

    @Test
    fun `opening todays bird before the first refresh still clears the dot`() =
        runTest {
            val tracker = tracker()
            // A notification tap can open the profile before the start-up refresh has finished.
            tracker.onSpeciesOpened("Q25403")
            assertEquals("2026-10-06", prefs.dailyBirdOpenedDate.first())
            tracker.refresh()
            assertFalse(tracker.showTabDot.first())
        }

    @Test
    fun `a failing history keeps the previous state instead of throwing`() =
        runTest {
            val tracker = tracker()
            tracker.refresh()
            val before = tracker.state.value
            history.failWith = IllegalStateException("database closed")
            tracker.refresh()
            assertEquals(before, tracker.state.value)
        }

    @Test
    fun `the dot is hidden without a bird and when opened today`() {
        val bird =
            DailyBirdToday(
                date = tuesday,
                speciesId = "Q25403",
                name = "Sävsångare",
                scientificName = "Acrocephalus schoenobaenus",
                heroImagePath = null,
                caughtToday = false,
                daysCaught = 0,
            )
        assertFalse(isDailyBirdDotVisible(bird = null, openedDate = null))
        assertTrue(isDailyBirdDotVisible(bird = bird, openedDate = null))
        assertTrue(isDailyBirdDotVisible(bird = bird, openedDate = "2026-10-05"))
        assertFalse(isDailyBirdDotVisible(bird = bird, openedDate = "2026-10-06"))
    }
}
