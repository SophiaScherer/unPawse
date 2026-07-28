package com.example.unpawse.service

import org.junit.Assert.assertEquals
import org.junit.Test

class UsageReminderTest {

    @Test
    fun `the title names the app`() {
        assertEquals("Instagram check-in", reminderTitle("Instagram"))
    }

    @Test
    fun `the body pairs time spent with time left`() {
        assertEquals("45m today · 15m left", reminderText(usedMinutes = 45, remainingMinutes = 15))
        assertEquals("2h today · 1h 30m left", reminderText(usedMinutes = 120, remainingMinutes = 90))
    }

    /** Remaining minutes are floored, so zero means "nearly out", not "none". */
    @Test
    fun `the last sub-minute is spelled out rather than shown as zero`() {
        assertEquals("59m today · under a minute left", reminderText(59, remainingMinutes = 0))
    }

    /** The app can stop being monitored between the timer starting and firing. */
    @Test
    fun `time left is omitted when there is no limit to report`() {
        assertEquals("45m today", reminderText(usedMinutes = 45, remainingMinutes = null))
    }

    @Test
    fun `a fresh session still reports honestly`() {
        assertEquals("0m today · 30m left", reminderText(usedMinutes = 0, remainingMinutes = 30))
    }
}
