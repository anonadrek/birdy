package se.birdy.domain.badge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BadgeRuleTest {
    @Test
    fun `ObservedBeforeHour carries hour and target`() {
        val rule = BadgeRule.ObservedBeforeHour(hour = 6, target = 5)
        assertEquals(6, rule.hour)
        assertEquals(5, rule.target)
    }

    @Test
    fun `SpeciesAcrossSeasons carries seasons and target`() {
        val rule = BadgeRule.SpeciesAcrossSeasons(seasons = 4, target = 1)
        assertEquals(4, rule.seasons)
        assertEquals(1, rule.target)
    }

    @Test
    fun `AudioObservationCount carries target`() {
        val rule = BadgeRule.AudioObservationCount(target = 5)
        assertEquals(5, rule.target)
    }

    @Test
    fun `ObservationsWithNote carries minLength and target`() {
        val rule = BadgeRule.ObservationsWithNote(minLength = 20, target = 10)
        assertEquals(20, rule.minLength)
        assertEquals(10, rule.target)
    }

    @Test
    fun `ObservedInAllSeasons carries target`() {
        val rule = BadgeRule.ObservedInAllSeasons(target = 1)
        assertEquals(1, rule.target)
    }

    @Test
    fun `Badge defaults isPremium to false`() {
        val b =
            Badge(
                id = "novice",
                category = BadgeCategory.PROGRESSION,
                rule = BadgeRule.CountUniqueSpecies(target = 5),
            )
        assertFalse(b.isPremium)
    }

    @Test
    fun `Badge accepts isPremium true`() {
        val b =
            Badge(
                id = "premium_dawn_chaser",
                category = BadgeCategory.PROGRESSION,
                rule = BadgeRule.ObservedBeforeHour(hour = 6, target = 5),
                isPremium = true,
            )
        assertTrue(b.isPremium)
    }
}
