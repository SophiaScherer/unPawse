package com.example.unpawse.data.capture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The rule behind `Capture.isBonus`. Two properties matter beyond the threshold itself: the streak
 * keeps qualifying past the milestone day (a 5-day streak is not less deserving than a 3-day one),
 * and only the day's *first* capture qualifies, or the shutter would mint bonuses all afternoon.
 */
class StreakMilestoneTest {

    private val today = LocalDate.of(2026, 7, 16)
    private fun days(vararg offsets: Long) = offsets.map { today.minusDays(it) }.toSet()

    @Test
    fun `the first ever capture is not a milestone`() {
        assertFalse(isStreakMilestone(emptySet(), today))
    }

    @Test
    fun `day two falls short`() {
        assertFalse(isStreakMilestone(days(1), today))
    }

    @Test
    fun `day three is the milestone`() {
        assertTrue(isStreakMilestone(days(1, 2), today))
    }

    @Test
    fun `every day past the milestone still qualifies`() {
        assertTrue(isStreakMilestone(days(1, 2, 3), today))
        assertTrue(isStreakMilestone(days(1, 2, 3, 4), today))
    }

    @Test
    fun `only the first capture of the day qualifies`() {
        assertFalse(isStreakMilestone(days(0, 1, 2), today))
    }

    @Test
    fun `a streak broken yesterday starts over`() {
        assertFalse(isStreakMilestone(days(2, 3, 4), today))
    }

    @Test
    fun `the threshold is configurable`() {
        assertTrue(isStreakMilestone(days(1), today, milestoneDays = 2))
        assertFalse(isStreakMilestone(days(1, 2), today, milestoneDays = 4))
    }
}
