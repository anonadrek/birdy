package se.birdy.app.ui.match

import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.disambig_eyebrow_one
import birdy_bird_scanner.composeapp.generated.resources.disambig_eyebrow_three
import birdy_bird_scanner.composeapp.generated.resources.disambig_eyebrow_two
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Release 1.3.0 Plan 3 Task 7 (device walkthrough, API 36 emulator): Disambig keeps only the
 * candidates above the disambig threshold, so it can show a single candidate. The eyebrow still
 * said "TVÅ KANDIDATER" (two candidates) above one card. It must name the number actually shown.
 */
class DisambigEyebrowTest {
    @Test
    fun `one candidate is called one candidate`() {
        assertEquals(Res.string.disambig_eyebrow_one, disambigEyebrowRes(candidateCount = 1))
    }

    @Test
    fun `two candidates are called two candidates`() {
        assertEquals(Res.string.disambig_eyebrow_two, disambigEyebrowRes(candidateCount = 2))
    }

    @Test
    fun `three candidates are called three candidates`() {
        assertEquals(Res.string.disambig_eyebrow_three, disambigEyebrowRes(candidateCount = 3))
    }
}
