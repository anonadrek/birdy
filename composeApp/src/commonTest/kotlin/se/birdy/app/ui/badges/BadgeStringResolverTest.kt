package se.birdy.app.ui.badges

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BadgeStringResolverTest {
    @Test
    fun humanizesUnderscoreIdToTitleCase() {
        assertEquals("First Find", humanizeBadgeId("first_find"))
    }

    @Test
    fun humanizeStripsPremiumPrefix() {
        assertEquals("Year Lister", humanizeBadgeId("premium_year_lister"))
    }

    @Test
    fun fallsBackToHumanizedIdWhenResourceLookupFails() =
        runTest {
            val resolved =
                resolveBadgeString("premium_year_lister") {
                    throw IllegalStateException("unknown badge id")
                }
            assertEquals("Year Lister", resolved)
        }

    @Test
    fun rethrowsCancellationFromResourceLookup() =
        runTest {
            assertFailsWith<CancellationException> {
                resolveBadgeString("first_find") { throw CancellationException("cancelled") }
            }
        }
}
