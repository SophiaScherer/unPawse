package com.example.unpawse.data.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.TUESDAY
import java.time.LocalTime

/** Pure schedule arithmetic — no Room, no clock, no device. */
class ScheduleMathTest {

    private fun window(
        id: Long = 1,
        label: String = "Test",
        packageName: String? = null,
        start: String = "22:00",
        end: String = "07:00",
        days: Int = EVERY_DAY_MASK,
        enabled: Boolean = true,
    ) = ScheduleWindow(
        id = id,
        label = label,
        packageName = packageName,
        startMinuteOfDay = minuteOfDay(LocalTime.parse(start)),
        endMinuteOfDay = minuteOfDay(LocalTime.parse(end)),
        daysMask = days,
        enabled = enabled,
    )

    private fun at(time: String) = minuteOfDay(LocalTime.parse(time))

    // --- Masks ------------------------------------------------------------------------------

    @Test
    fun `Monday is the first bit and Sunday the last`() {
        assertEquals(0b000_0001, dayBit(MONDAY))
        assertEquals(0b100_0000, dayBit(SUNDAY))
    }

    @Test
    fun `the named masks select the days they claim to`() {
        assertEquals(listOf(MONDAY, TUESDAY), daysIn(dayBit(MONDAY) or dayBit(TUESDAY)))
        assertEquals(5, daysIn(WEEKDAYS_MASK).size)
        assertEquals(listOf(SATURDAY, SUNDAY), daysIn(WEEKENDS_MASK))
        assertEquals(7, daysIn(EVERY_DAY_MASK).size)
    }

    @Test
    fun `a mask round-trips through daysMaskOf`() {
        val days = listOf(MONDAY, FRIDAY, SUNDAY)
        assertEquals(days, daysIn(daysMaskOf(days)))
    }

    @Test
    fun `minute of day counts from local midnight`() {
        assertEquals(0, at("00:00"))
        assertEquals(22 * 60, at("22:00"))
        assertEquals(MINUTES_PER_DAY - 1, at("23:59"))
    }

    @Test
    fun `wrapping a minute of day stays inside one day in both directions`() {
        assertEquals(0, wrapMinuteOfDay(MINUTES_PER_DAY))
        assertEquals(MINUTES_PER_DAY - 60, wrapMinuteOfDay(-60))
    }

    // --- Same-day windows -------------------------------------------------------------------

    @Test
    fun `a same-day window covers its own hours`() {
        val school = window(start = "09:00", end = "15:00", days = WEEKDAYS_MASK)

        assertTrue(isActiveAt(school, MONDAY, at("09:00")))
        assertTrue(isActiveAt(school, MONDAY, at("12:30")))
        assertFalse(isActiveAt(school, MONDAY, at("08:59")))
    }

    @Test
    fun `the end of a same-day window is exclusive`() {
        val school = window(start = "09:00", end = "15:00", days = WEEKDAYS_MASK)

        assertTrue(isActiveAt(school, MONDAY, at("14:59")))
        assertFalse(isActiveAt(school, MONDAY, at("15:00")))
    }

    @Test
    fun `a window is inert on a day its mask does not select`() {
        val school = window(start = "09:00", end = "15:00", days = WEEKDAYS_MASK)

        assertFalse(isActiveAt(school, SATURDAY, at("12:00")))
    }

    @Test
    fun `a disabled window never fires`() {
        val off = window(start = "09:00", end = "15:00", enabled = false)

        assertFalse(isActiveAt(off, MONDAY, at("12:00")))
    }

    // --- Wrapping windows -------------------------------------------------------------------

    @Test
    fun `a wrapping window covers the evening of the day it starts`() {
        val bedtime = window(start = "22:00", end = "07:00", days = dayBit(FRIDAY))

        assertTrue(isActiveAt(bedtime, FRIDAY, at("22:00")))
        assertTrue(isActiveAt(bedtime, FRIDAY, at("23:59")))
    }

    @Test
    fun `a wrapping window covers the next morning, matched against the previous day`() {
        // "Friday night" means Fri 22:00 → Sat 07:00, so Saturday morning is covered even though
        // Saturday itself is not in the mask.
        val bedtime = window(start = "22:00", end = "07:00", days = dayBit(FRIDAY))

        assertTrue(isActiveAt(bedtime, SATURDAY, at("00:30")))
        assertTrue(isActiveAt(bedtime, SATURDAY, at("06:59")))
        assertFalse(isActiveAt(bedtime, SATURDAY, at("07:00")))
    }

    @Test
    fun `a wrapping window leaves the gap between its end and its next start alone`() {
        val bedtime = window(start = "22:00", end = "07:00", days = dayBit(FRIDAY))

        assertFalse(isActiveAt(bedtime, FRIDAY, at("12:00")))
        assertFalse(isActiveAt(bedtime, SATURDAY, at("12:00")))
    }

    @Test
    fun `a wrapping window does not start on a day outside its mask`() {
        val bedtime = window(start = "22:00", end = "07:00", days = dayBit(FRIDAY))

        assertFalse(isActiveAt(bedtime, SATURDAY, at("23:00")))
        // …and Sunday morning is not covered either, because Saturday never started it.
        assertFalse(isActiveAt(bedtime, SUNDAY, at("03:00")))
    }

    @Test
    fun `Monday morning is covered by a Sunday-night window`() {
        // The previous-day lookup has to wrap around the start of the week.
        val bedtime = window(start = "22:00", end = "07:00", days = dayBit(SUNDAY))

        assertTrue(isActiveAt(bedtime, MONDAY, at("06:00")))
    }

    @Test
    fun `an equal start and end reads as a full 24 hours`() {
        val allDay = window(start = "08:00", end = "08:00", days = dayBit(MONDAY))

        assertTrue(isActiveAt(allDay, MONDAY, at("08:00")))
        assertTrue(isActiveAt(allDay, MONDAY, at("23:59")))
        assertTrue(isActiveAt(allDay, TUESDAY, at("07:59")))
        // Exactly 24 hours later the window is over, and Tuesday isn't selected to start a new one.
        assertFalse(isActiveAt(allDay, TUESDAY, at("08:00")))
        assertFalse(isActiveAt(allDay, MONDAY, at("07:59")))
    }

    // --- Scoping ----------------------------------------------------------------------------

    @Test
    fun `a global window blocks any app`() {
        val windows = listOf(window(packageName = null, start = "09:00", end = "17:00"))

        assertEquals(1L, activeWindowFor(windows, "com.ig", MONDAY, at("10:00"))?.id)
        assertEquals(1L, activeWindowFor(windows, "com.tiktok", MONDAY, at("10:00"))?.id)
    }

    @Test
    fun `a per-app window blocks only that app`() {
        val windows = listOf(window(packageName = "com.ig", start = "09:00", end = "17:00"))

        assertEquals(1L, activeWindowFor(windows, "com.ig", MONDAY, at("10:00"))?.id)
        assertNull(activeWindowFor(windows, "com.tiktok", MONDAY, at("10:00")))
    }

    @Test
    fun `no active window means nothing is blocked`() {
        val windows = listOf(window(start = "09:00", end = "17:00"))

        assertNull(activeWindowFor(windows, "com.ig", MONDAY, at("18:00")))
        assertNull(activeWindowFor(emptyList(), "com.ig", MONDAY, at("10:00")))
    }

    @Test
    fun `the earliest-starting active window wins when two overlap`() {
        // The DAO orders by start, so the caller sees them in this order.
        val windows = listOf(
            window(id = 1, label = "Morning", start = "09:00", end = "12:00"),
            window(id = 2, label = "All day", start = "10:00", end = "18:00"),
        )

        assertEquals("Morning", activeWindowFor(windows, "com.ig", MONDAY, at("11:00"))?.label)
        assertEquals("All day", activeWindowFor(windows, "com.ig", MONDAY, at("13:00"))?.label)
    }

    @Test
    fun `a disabled window is skipped in favour of an active one`() {
        val windows = listOf(
            window(id = 1, label = "Off", start = "09:00", end = "18:00", enabled = false),
            window(id = 2, label = "On", start = "10:00", end = "18:00"),
        )

        assertEquals("On", activeWindowFor(windows, "com.ig", MONDAY, at("11:00"))?.label)
    }

    @Test
    fun `every day of the week can start a window`() {
        val allWeek = window(start = "20:00", end = "21:00", days = EVERY_DAY_MASK)

        DayOfWeek.values().forEach { day ->
            assertTrue("$day should be covered", isActiveAt(allWeek, day, at("20:30")))
        }
    }
}
