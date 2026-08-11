package com.example.unpawse.service

import com.example.unpawse.data.schedule.EVERY_DAY_MASK
import com.example.unpawse.data.schedule.ScheduleWindow
import com.example.unpawse.data.schedule.WEEKDAYS_MASK
import com.example.unpawse.data.schedule.WEEKENDS_MASK
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * The gate itself owns no rule — [com.example.unpawse.data.schedule.ScheduleMath] is tested
 * separately — so these cover the wiring: the clock it reads, and that edits land on the next call.
 */
class ScheduleGateTest {

    // A Wednesday.
    private var nowValue = LocalDateTime.parse("2026-07-15T12:00")
    private var windowList = emptyList<ScheduleWindow>()
    private val gate = ScheduleGate(windows = { windowList }, now = { nowValue })

    private fun window(
        id: Long = 1,
        label: String = "Test",
        packageName: String? = null,
        startHour: Int = 9,
        endHour: Int = 17,
        days: Int = EVERY_DAY_MASK,
        enabled: Boolean = true,
    ) = ScheduleWindow(
        id = id,
        label = label,
        packageName = packageName,
        startMinuteOfDay = startHour * 60,
        endMinuteOfDay = endHour * 60,
        daysMask = days,
        enabled = enabled,
    )

    @Test
    fun `with no windows nothing is blocked`() {
        assertNull(gate.activeWindowFor("com.ig"))
        assertFalse(gate.isBlocked("com.ig"))
    }

    @Test
    fun `the gate reports the window covering the current moment`() {
        windowList = listOf(window(label = "Work"))

        assertEquals("Work", gate.activeWindowFor("com.ig")?.label)
        assertTrue(gate.isBlocked("com.ig"))
    }

    @Test
    fun `moving the clock out of the window releases the app`() {
        windowList = listOf(window(startHour = 9, endHour = 17))
        assertTrue("precondition: blocked at noon", gate.isBlocked("com.ig"))

        nowValue = nowValue.withHour(18)

        assertFalse(gate.isBlocked("com.ig"))
    }

    @Test
    fun `the day of week comes from the clock, not the caller`() {
        windowList = listOf(window(days = WEEKENDS_MASK))
        assertFalse("precondition: Wednesday is not a weekend", gate.isBlocked("com.ig"))

        nowValue = nowValue.plusDays(3) // Saturday

        assertTrue(gate.isBlocked("com.ig"))
    }

    @Test
    fun `a window edited between ticks takes effect on the next read`() {
        // The gate holds no snapshot of its own — it asks the lambda every time, which is what lets
        // a schedule change apply without restarting the monitor service.
        assertFalse(gate.isBlocked("com.ig"))

        windowList = listOf(window(days = WEEKDAYS_MASK))

        assertTrue(gate.isBlocked("com.ig"))
    }

    @Test
    fun `per-app scoping is honoured`() {
        windowList = listOf(window(packageName = "com.ig"))

        assertTrue(gate.isBlocked("com.ig"))
        assertFalse(gate.isBlocked("com.tiktok"))
    }
}
