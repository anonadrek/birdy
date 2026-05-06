package se.birdy.app.testing

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class FakeClock(
    var now: Instant = Instant.fromEpochMilliseconds(0),
) : Clock {
    override fun now(): Instant = now
}
