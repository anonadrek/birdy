package se.birdy.app.ui.match

import kotlin.test.Test
import kotlin.test.assertEquals

class MatchThresholdsTest {
    @Test
    fun match_confidence_constant_is_0_50() {
        assertEquals(0.50f, MatchThresholds.MATCH_CONFIDENCE)
    }

    @Test
    fun disambig_confidence_constant_is_0_35() {
        assertEquals(0.35f, MatchThresholds.DISAMBIG_CONFIDENCE)
    }

    @Test
    fun routeFor_at_exactly_match_threshold_routes_to_match() {
        assertEquals(MatchRoute.MATCH, MatchThresholds.routeFor(0.50f))
    }

    @Test
    fun routeFor_just_below_match_threshold_routes_to_disambig() {
        assertEquals(MatchRoute.DISAMBIG, MatchThresholds.routeFor(0.4999f))
    }

    @Test
    fun routeFor_at_exactly_disambig_threshold_routes_to_disambig() {
        assertEquals(MatchRoute.DISAMBIG, MatchThresholds.routeFor(0.35f))
    }

    @Test
    fun routeFor_just_below_disambig_threshold_routes_to_nobird() {
        assertEquals(MatchRoute.NOBIRD, MatchThresholds.routeFor(0.3499f))
    }

    @Test
    fun routeFor_zero_routes_to_nobird() {
        assertEquals(MatchRoute.NOBIRD, MatchThresholds.routeFor(0f))
    }

    @Test
    fun routeFor_one_routes_to_match() {
        assertEquals(MatchRoute.MATCH, MatchThresholds.routeFor(1f))
    }
}
