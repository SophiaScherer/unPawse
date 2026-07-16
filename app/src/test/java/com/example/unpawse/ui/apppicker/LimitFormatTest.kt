package com.example.unpawse.ui.apppicker

import org.junit.Assert.assertEquals
import org.junit.Test

class LimitFormatTest {

    @Test
    fun `formats sub-hour limits as minutes`() {
        assertEquals("45m", formatLimit(45))
        assertEquals("15m", formatLimit(15))
    }

    @Test
    fun `formats whole hours without a minutes part`() {
        assertEquals("1h", formatLimit(60))
        assertEquals("8h", formatLimit(480))
    }

    @Test
    fun `formats mixed hours and minutes`() {
        assertEquals("1h 30m", formatLimit(90))
        assertEquals("2h 15m", formatLimit(135))
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
