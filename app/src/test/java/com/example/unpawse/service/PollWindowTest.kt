package com.example.unpawse.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Where each poll of the foreground monitor reads from, including when the wall clock moves. */
class PollWindowTest {

    private val now = 1_800_000_000_000L

    @Test
    fun `an ordinary poll reads from where the last one stopped`() {
        assertEquals(PollWindow(now - 1_000L, now), pollWindow(cursor = now - 1_000L, tick = now))
    }

    @Test
    fun `a screen-off gap is read in full on waking`() {
        val eightHours = 8L * 60 * 60 * 1000
        assertEquals(PollWindow(now - eightHours, now), pollWindow(cursor = now - eightHours, tick = now))
    }

    @Test
    fun `a clock moved back restarts the window at the new time`() {
        val fortyMinutes = 40L * 60 * 1000
        val window = pollWindow(cursor = now, tick = now - fortyMinutes)

        // Begin before end, so the platform has something to answer; it used to be 40 minutes after.
        assertTrue(window.beginMillis < window.endMillis)
        assertEquals(now - fortyMinutes, window.endMillis)
        assertEquals(CLOCK_CHANGE_OVERLAP_MILLIS, window.endMillis - window.beginMillis)
    }

    @Test
    fun `the poll after a clock change carries on normally`() {
        val back = now - 40L * 60 * 1000
        val first = pollWindow(cursor = now, tick = back)
        val next = pollWindow(cursor = first.endMillis, tick = back + 1_000L)

        assertEquals(PollWindow(back, back + 1_000L), next)
    }

    @Test
    fun `a clock moved far forward reads a bounded window`() {
        val yearAhead = now + 365L * 24 * 60 * 60 * 1000
        val window = pollWindow(cursor = now, tick = yearAhead)

        assertEquals(yearAhead, window.endMillis)
        assertEquals(MAX_CATCH_UP_MILLIS, window.endMillis - window.beginMillis)
    }
}
