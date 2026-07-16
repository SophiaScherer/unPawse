package com.example.unpawse.data.usage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure limit arithmetic — no Room, no device. */
class UsageMathTest {

    @Test
    fun `remaining subtracts used from the limit`() {
        // 60-min limit, 20 min used, nothing earned → 40 min left.
        assertEquals(40, remainingMinutes(limitMinutes = 60, usedSeconds = 20 * 60L, earnedSeconds = 0))
    }

    @Test
    fun `earned minutes extend the budget`() {
        // 30-min limit fully used, +15 earned back → 15 min left.
        assertEquals(15, remainingMinutes(limitMinutes = 30, usedSeconds = 30 * 60L, earnedSeconds = 15 * 60L))
    }

    @Test
    fun `remaining minutes floor at zero when over the limit`() {
        assertEquals(0, remainingMinutes(limitMinutes = 10, usedSeconds = 25 * 60L, earnedSeconds = 0))
    }

    @Test
    fun `remaining minutes floor toward whole minutes`() {
        // 90 seconds left → 1 whole minute.
        assertEquals(1, remainingMinutes(limitMinutes = 2, usedSeconds = 30L, earnedSeconds = 0))
    }

    @Test
    fun `limit reached exactly at the boundary`() {
        assertTrue(isLimitReached(limitMinutes = 10, usedSeconds = 10 * 60L, earnedSeconds = 0))
    }

    @Test
    fun `limit not reached with time to spare`() {
        assertFalse(isLimitReached(limitMinutes = 10, usedSeconds = 5 * 60L, earnedSeconds = 0))
    }

    @Test
    fun `earned time can lift an over-limit app back under`() {
        // Used past the limit but earned enough to be back in budget.
        assertFalse(isLimitReached(limitMinutes = 10, usedSeconds = 12 * 60L, earnedSeconds = 5 * 60L))
    }

    @Test
    fun `signed remaining seconds go negative when over`() {
        assertEquals(-120L, remainingSeconds(limitMinutes = 10, usedSeconds = 12 * 60L, earnedSeconds = 0))
    }
}
