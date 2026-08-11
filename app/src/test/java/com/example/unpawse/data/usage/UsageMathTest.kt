package com.example.unpawse.data.usage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek

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

    @Test
    fun `a zero limit is still over budget from the first second`() {
        // The sentinel for "no cap" is negative precisely so this stays true.
        assertTrue(isLimitReached(limitMinutes = 0, usedSeconds = 1L, earnedSeconds = 0))
    }

    @Test
    fun `an uncapped day is never limit-reached however long it runs`() {
        assertFalse(isLimitReached(UNLIMITED_MINUTES, usedSeconds = 12 * 60 * 60L, earnedSeconds = 0))
    }

    @Test
    fun `an uncapped day has no remaining minutes to show`() {
        assertNull(remainingMinutes(UNLIMITED_MINUTES, usedSeconds = 60L, earnedSeconds = 0))
    }
}

/** The weekday/weekend split — which of an app's two budgets applies on a given day. */
class EffectiveLimitTest {

    @Test
    fun `without an override every day uses the everyday budget`() {
        DayOfWeek.values().forEach { day ->
            assertEquals(30, effectiveLimitMinutes(dailyLimitMinutes = 30, weekendLimitMinutes = null, day = day))
        }
    }

    @Test
    fun `an override applies on Saturday and Sunday only`() {
        assertEquals(120, effectiveLimitMinutes(30, weekendLimitMinutes = 120, day = DayOfWeek.SATURDAY))
        assertEquals(120, effectiveLimitMinutes(30, weekendLimitMinutes = 120, day = DayOfWeek.SUNDAY))
        assertEquals(30, effectiveLimitMinutes(30, weekendLimitMinutes = 120, day = DayOfWeek.FRIDAY))
        assertEquals(30, effectiveLimitMinutes(30, weekendLimitMinutes = 120, day = DayOfWeek.MONDAY))
    }

    @Test
    fun `an unlimited override leaves weekends uncapped but keeps weekdays limited`() {
        // "Weekday-only limits": 30m Mon–Fri, no cap at the weekend.
        val saturday = effectiveLimitMinutes(30, UNLIMITED_MINUTES, DayOfWeek.SATURDAY)
        val wednesday = effectiveLimitMinutes(30, UNLIMITED_MINUTES, DayOfWeek.WEDNESDAY)

        assertFalse(isLimitReached(saturday, usedSeconds = 5 * 60 * 60L, earnedSeconds = 0))
        assertTrue(isLimitReached(wednesday, usedSeconds = 31 * 60L, earnedSeconds = 0))
    }

    @Test
    fun `an override can also tighten the weekend`() {
        assertEquals(15, effectiveLimitMinutes(60, weekendLimitMinutes = 15, day = DayOfWeek.SUNDAY))
    }
}
