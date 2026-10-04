package com.example.unpawse.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Where each poll of the foreground monitor reads from, including when the wall clock moves. */
class PollWindowTest {

    private val now = 1_800_000_000_000L
    private val hour = 60L * 60 * 1000

    @Test
    fun `an ordinary poll reads from where the last one stopped`() {
        assertEquals(PollWindow(now - 1_000L, now), pollWindow(cursor = now - 1_000L, tick = now, elapsedMillis = 1_000L))
    }

    @Test
    fun `a screen-off gap is read in full on waking`() {
        assertEquals(PollWindow(now - 8 * hour, now), pollWindow(now - 8 * hour, now, elapsedMillis = 8 * hour))
    }

    /** Truncating this lost the stop that says the app left, and the stale app was credited on the lock screen. */
    @Test
    fun `a screen-off gap longer than a day is still read in full`() {
        val gap = 30 * hour
        assertEquals(PollWindow(now - gap, now), pollWindow(now - gap, now, elapsedMillis = gap))
    }

    @Test
    fun `a clock moved back restarts the window at the new time`() {
        val fortyMinutes = 40L * 60 * 1000
        val window = pollWindow(cursor = now, tick = now - fortyMinutes, elapsedMillis = 1_000L)

        // Begin before end, so the platform has something to answer; it used to be 40 minutes after.
        assertTrue(window.beginMillis < window.endMillis)
        assertEquals(now - fortyMinutes, window.endMillis)
        assertEquals(1_000L + CLOCK_CHANGE_OVERLAP_MILLIS, window.endMillis - window.beginMillis)
    }

    /** The gap's events are re-stamped into the elapsed span before the new time; reading less lost the stop. */
    @Test
    fun `a clock moved back during a screen-off gap still reads the whole gap`() {
        val gap = 3 * hour
        val lessThanGap = pollWindow(cursor = now, tick = now + 2 * hour, elapsedMillis = gap)
        val moreThanGap = pollWindow(cursor = now, tick = now - 5 * hour, elapsedMillis = gap)

        assertEquals(PollWindow(now - hour - CLOCK_CHANGE_OVERLAP_MILLIS, now + 2 * hour), lessThanGap)
        assertEquals(PollWindow(now - 8 * hour - CLOCK_CHANGE_OVERLAP_MILLIS, now - 5 * hour), moreThanGap)
    }

    @Test
    fun `the poll after a clock change carries on normally`() {
        val back = now - 40L * 60 * 1000
        val first = pollWindow(cursor = now, tick = back, elapsedMillis = 1_000L)
        val next = pollWindow(cursor = first.endMillis, tick = back + 1_000L, elapsedMillis = 1_000L)

        assertEquals(PollWindow(back, back + 1_000L), next)
    }

    @Test
    fun `a clock moved forward reads only the real time that passed`() {
        val yearAhead = now + 365L * 24 * hour
        val window = pollWindow(cursor = now, tick = yearAhead, elapsedMillis = 1_000L)

        assertEquals(yearAhead, window.endMillis)
        assertEquals(1_000L + CLOCK_CHANGE_OVERLAP_MILLIS, window.endMillis - window.beginMillis)
    }

    @Test
    fun `small drift between the clocks is not a jump`() {
        assertEquals(PollWindow(now - 1_500L, now), pollWindow(now - 1_500L, now, elapsedMillis = 1_000L))
    }
}
