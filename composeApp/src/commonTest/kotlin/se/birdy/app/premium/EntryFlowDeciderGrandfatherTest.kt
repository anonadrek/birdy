package se.birdy.app.premium

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EntryFlowDeciderGrandfatherTest {
    @Test
    fun `grandfathered user sees thanks once`() {
        assertTrue(EntryFlowDecider.shouldShowGrandfatherThanks(isGrandfathered = true, alreadyShown = false))
    }

    @Test
    fun `thanks is never shown twice`() {
        assertFalse(EntryFlowDecider.shouldShowGrandfatherThanks(isGrandfathered = true, alreadyShown = true))
    }

    @Test
    fun `new users never see thanks`() {
        assertFalse(EntryFlowDecider.shouldShowGrandfatherThanks(isGrandfathered = false, alreadyShown = false))
    }
}
