package com.example.unpawse.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StreakTest {

    private val today = LocalDate.of(2026, 7, 16)
    private fun days(vararg offsets: Long) = offsets.map { today.minusDays(it) }.toSet()

    @Test
    fun `no captures is no streak`() {
        assertEquals(0, currentStreakDays(emptySet(), today))
    }

    @Test
    fun `consecutive days ending today count`() {
        assertEquals(3, currentStreakDays(days(0, 1, 2), today))
    }

    @Test
    fun `a gap ends the streak`() {
        // Captured today, yesterday, then nothing the day before.
        assertEquals(2, currentStreakDays(days(0, 1, 3, 4), today))
    }

    @Test
    fun `an unphotographed today does not break a streak in progress`() {
        // Nothing today yet, but yesterday and the day before — the streak is still alive.
        assertEquals(2, currentStreakDays(days(1, 2), today))
    }

    @Test
    fun `a stale streak is dead once two days pass`() {
        assertEquals(0, currentStreakDays(days(2, 3, 4), today))
    }

    @Test
    fun `longest streak finds the best historical run`() {
        // Runs of 2 and 4; the 4 wins even though it's older.
        val dates = days(0, 1) + days(5, 6, 7, 8)
        assertEquals(4, longestStreakDays(dates))
    }

    @Test
    fun `longest streak of a single day is one`() {
        assertEquals(1, longestStreakDays(days(3)))
    }

    @Test
    fun `longest streak of nothing is zero`() {
        assertEquals(0, longestStreakDays(emptySet()))
    }
}
