package com.example.unpawse.ui.apppicker

import com.example.unpawse.ui.format.formatMinutes
import com.example.unpawse.ui.format.formatSeconds
import org.junit.Assert.assertEquals
import org.junit.Test

class LimitFormatTest {

    @Test
    fun `formats sub-hour limits as minutes`() {
        assertEquals("45m", formatMinutes(45))
        assertEquals("15m", formatMinutes(15))
    }

    @Test
    fun `formats whole hours without a minutes part`() {
        assertEquals("1h", formatMinutes(60))
        assertEquals("8h", formatMinutes(480))
    }

    @Test
    fun `formats mixed hours and minutes`() {
        assertEquals("1h 30m", formatMinutes(90))
        assertEquals("2h 15m", formatMinutes(135))
    }

    @Test
    fun `formats from seconds, flooring to whole minutes`() {
        assertEquals("2h 15m", formatSeconds(8_100))
        assertEquals("0m", formatSeconds(59))
    }

    @Test
    fun `negative durations clamp to zero rather than rendering nonsense`() {
        assertEquals("0m", formatMinutes(-5))
    }

    @Test
    fun `stepping moves by one increment`() {
        assertEquals(45, adjustLimit(30, +1))
        assertEquals(15, adjustLimit(30, -1))
    }

    @Test
    fun `stepping clamps to the allowed band`() {
        assertEquals(MIN_LIMIT_MINUTES, adjustLimit(MIN_LIMIT_MINUTES, -1))
        assertEquals(MAX_LIMIT_MINUTES, adjustLimit(MAX_LIMIT_MINUTES, +1))
    }
}
