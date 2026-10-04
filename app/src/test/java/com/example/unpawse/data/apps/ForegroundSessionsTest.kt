package com.example.unpawse.data.apps

import com.example.unpawse.data.apps.ForegroundEvent.Kind.DEVICE_BOUNDARY
import com.example.unpawse.data.apps.ForegroundEvent.Kind.LEFT
import com.example.unpawse.data.apps.ForegroundEvent.Kind.RESUMED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** Turning usage events into per-day foreground time, without `UsageStatsManager`. */
class ForegroundSessionsTest {

    private val zone = ZoneId.of("America/Los_Angeles")
    private val today = LocalDate.of(2026, 10, 4)
    private val windows = dayWindows(today, days = 3, zone = zone)
    private val begin = windows.first().beginMillis
    private val minute = 60_000L
    private val hour = 60 * minute

    /** Epoch millis for [hours]:[minutes] on the day [daysAgo] before today. */
    private fun at(daysAgo: Long, hours: Int, minutes: Int = 0): Long =
        today.minusDays(daysAgo).atTime(hours, minutes).atZone(zone).toInstant().toEpochMilli()

    private fun event(pkg: String, time: Long, kind: ForegroundEvent.Kind, activity: String = "Main") =
        ForegroundEvent(pkg, activity, time, kind)

    private fun byDay(events: List<ForegroundEvent>, end: Long = windows.last().endMillis) =
        secondsByDay(foregroundIntervals(events, begin, end), events.map { it.timeMillis }, windows)

    @Test
    fun `a resume and a pause make one span`() {
        val days = byDay(listOf(event("chrome", at(0, 9), RESUMED), event("chrome", at(0, 9, 30), LEFT)))

        assertEquals(30 * 60L, days.getValue(today.toString()).getValue("chrome"))
    }

    @Test
    fun `a session across midnight is split between the two days`() {
        val days = byDay(listOf(event("yt", at(1, 23, 40), RESUMED), event("yt", at(0, 0, 15), LEFT)))

        assertEquals(20 * 60L, days.getValue(today.minusDays(1).toString()).getValue("yt"))
        assertEquals(15 * 60L, days.getValue(today.toString()).getValue("yt"))
    }

    @Test
    fun `an app still in front is counted up to the end of the read`() {
        val now = at(0, 10)
        val days = byDay(listOf(event("chrome", at(0, 9), RESUMED)), end = now)

        assertEquals(3600L, days.getValue(today.toString()).getValue("chrome"))
    }

    @Test
    fun `a departure with no arrival credits nothing before it`() {
        val days = byDay(listOf(event("chrome", at(0, 14), LEFT), event("yt", at(0, 15), RESUMED)), end = at(0, 16))

        assertFalse(days.getValue(today.toString()).containsKey("chrome"))
        assertFalse(days.containsKey(today.minusDays(2).toString()))
        assertEquals(3600L, days.getValue(today.toString()).getValue("yt"))
    }

    @Test
    fun `out-of-order events never count the same time twice`() {
        val days = byDay(
            listOf(
                event("a", at(0, 9), RESUMED),
                event("b", at(0, 10), RESUMED),
                event("a", at(0, 9, 30), LEFT),
                event("b", at(0, 11), LEFT),
            ),
        )

        assertTrue(days.getValue(today.toString()).values.sum() <= 2 * 3600L)
    }

    @Test
    fun `a stop after a pause does not count the session twice`() {
        val days = byDay(
            listOf(
                event("chrome", at(0, 9), RESUMED),
                event("chrome", at(0, 9, 10), LEFT),
                event("chrome", at(0, 9, 11), LEFT),
            ),
        )

        assertEquals(600L, days.getValue(today.toString()).getValue("chrome"))
    }

    @Test
    fun `two activities of one app overlapping in a transition count once`() {
        val days = byDay(
            listOf(
                event("chrome", at(0, 9), RESUMED, "Main"),
                event("chrome", at(0, 9, 20), RESUMED, "Settings"),
                event("chrome", at(0, 9, 21), LEFT, "Main"),
                event("chrome", at(0, 9, 30), LEFT, "Settings"),
            ),
        )

        assertEquals(30 * 60L, days.getValue(today.toString()).getValue("chrome"))
    }

    /** An activity left resumed underneath (a secondary-display launcher) used to add a day to every day. */
    @Test
    fun `only the app on top is credited, so time is never counted twice`() {
        val days = byDay(
            listOf(
                event("launcher2", at(0, 8), RESUMED),
                event("chrome", at(0, 9), RESUMED),
                event("chrome", at(0, 9, 30), LEFT),
                event("yt", at(0, 9, 30), RESUMED),
                event("yt", at(0, 10), LEFT),
            ),
            end = at(0, 10),
        )
        val today = days.getValue(today.toString())

        assertEquals(30 * 60L, today.getValue("chrome"))
        assertEquals(30 * 60L, today.getValue("yt"))
        assertEquals(3600L, today.getValue("launcher2"))
        assertEquals("two hours, not three", 2 * 3600L, today.values.sum())
    }

    @Test
    fun `leaving an app uncovers the one beneath it`() {
        val days = byDay(
            listOf(
                event("chrome", at(0, 9), RESUMED),
                event("recents", at(0, 9, 10), RESUMED),
                event("recents", at(0, 9, 11), LEFT),
                event("chrome", at(0, 9, 20), LEFT),
            ),
        )

        assertEquals(19 * 60L, days.getValue(today.toString()).getValue("chrome"))
    }

    @Test
    fun `a reboot closes whatever was left open`() {
        val days = byDay(
            listOf(
                event("chrome", at(0, 9), RESUMED),
                event("launcher", at(0, 9, 5), RESUMED),
                event("launcher", at(0, 9, 5), LEFT),
                event("android", at(0, 15), DEVICE_BOUNDARY),
            ),
        )

        // Not six hours: nothing survives a reboot, so the span ends at the last event before it.
        assertEquals(300L, days.getValue(today.toString()).getValue("chrome"))
    }

    @Test
    fun `a day the platform holds no events for is absent, not zero`() {
        val days = byDay(listOf(event("chrome", at(0, 9), RESUMED), event("chrome", at(0, 9, 30), LEFT)))

        assertFalse(days.containsKey(today.minusDays(2).toString()))
        assertFalse(days.containsKey(today.minusDays(1).toString()))
    }

    @Test
    fun `a day with events but no foreground time is a measured zero`() {
        val days = byDay(listOf(event("android", at(1, 8), DEVICE_BOUNDARY), event("chrome", at(0, 9), RESUMED)))

        assertTrue(days.getValue(today.minusDays(1).toString()).isEmpty())
    }

    @Test
    fun `a session spanning a whole day with no events in it still measures that day`() {
        val days = byDay(listOf(event("video", at(2, 22), RESUMED), event("video", at(0, 1), LEFT)))

        assertEquals(24 * 3600L, days.getValue(today.minusDays(1).toString()).getValue("video"))
    }

    @Test
    fun `totals add up every span of a package`() {
        val spans = foregroundIntervals(
            listOf(
                event("chrome", at(0, 9), RESUMED),
                event("chrome", at(0, 9, 10), LEFT),
                event("chrome", at(0, 11), RESUMED),
                event("chrome", at(0, 11, 5), LEFT),
            ),
            begin,
            windows.last().endMillis,
        )

        assertEquals(15 * minute, totalMillisByPackage(spans).getValue("chrome"))
    }

    @Test
    fun `events outside the read are clipped to it`() {
        val spans = foregroundIntervals(listOf(event("chrome", begin - hour, RESUMED)), begin, begin + hour)

        assertEquals(listOf(ForegroundInterval("chrome", begin, begin + hour)), spans)
    }
}
