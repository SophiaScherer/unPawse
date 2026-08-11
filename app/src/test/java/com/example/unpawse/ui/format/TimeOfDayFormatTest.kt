package com.example.unpawse.ui.format

import com.example.unpawse.data.schedule.MINUTES_PER_DAY
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/** Wall-clock copy for schedules. The locale is pinned so this doesn't depend on the test machine. */
class TimeOfDayFormatTest {

    private val us = Locale.US
    private val uk = Locale.UK

    @Test
    fun `a 12-hour locale gets AM and PM`() {
        assertEquals("10:00 PM", formatMinuteOfDay(22 * 60, us))
        assertEquals("7:00 AM", formatMinuteOfDay(7 * 60, us))
    }

    @Test
    fun `a 24-hour locale gets 24-hour time`() {
        assertEquals("22:00", formatMinuteOfDay(22 * 60, uk))
    }

    @Test
    fun `midnight and the last minute of the day both render`() {
        assertEquals("12:00 AM", formatMinuteOfDay(0, us))
        assertEquals("11:59 PM", formatMinuteOfDay(MINUTES_PER_DAY - 1, us))
    }

    @Test
    fun `a minute past the end of the day wraps rather than throwing`() {
        assertEquals("12:00 AM", formatMinuteOfDay(MINUTES_PER_DAY, us))
    }

    @Test
    fun `a range reads start to end`() {
        assertEquals("10:00 PM – 7:00 AM", formatTimeRange(22 * 60, 7 * 60, us))
    }

    @Test
    fun `minutes until an end time later today`() {
        assertEquals(120, minutesUntil(fromMinuteOfDay = 15 * 60, endMinuteOfDay = 17 * 60))
    }

    @Test
    fun `minutes until an end time past midnight`() {
        // 23:00 → 07:00 is eight hours, not minus sixteen.
        assertEquals(8 * 60, minutesUntil(fromMinuteOfDay = 23 * 60, endMinuteOfDay = 7 * 60))
    }

    @Test
    fun `an end time equal to now still has a full day to run`() {
        // A window that ends this very minute is still blocking, so zero would be wrong.
        assertEquals(MINUTES_PER_DAY, minutesUntil(fromMinuteOfDay = 9 * 60, endMinuteOfDay = 9 * 60))
    }
}
