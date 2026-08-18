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

/** The whole-day budget the Home ring and the Stats figure both report from. */
class DailyBudgetTest {

    private val weekday = DayOfWeek.THURSDAY
    private val weekend = DayOfWeek.SATURDAY

    private fun app(pkg: String, limitMinutes: Int, weekendLimitMinutes: Int? = null) =
        MonitoredApp(pkg, pkg, limitMinutes, enabled = true, weekendLimitMinutes = weekendLimitMinutes)

    private fun usage(pkg: String, usedMinutes: Int, earnedMinutes: Int = 0) =
        pkg to DailyUsage(pkg, "2026-07-16", usedMinutes * 60L, earnedMinutes * 60L)

    @Test
    fun `budget sums the capped apps and their usage`() {
        val budget = dailyBudget(
            listOf(app("a", 60), app("b", 30)),
            mapOf(usage("a", 15), usage("b", 15)),
            weekday,
        )!!

        assertEquals(90 * 60L, budget.budgetSeconds)
        assertEquals(30 * 60L, budget.usedSeconds)
        assertEquals(60 * 60L, budget.remainingSeconds)
        assertEquals(66, budget.leftPercent)
    }

    @Test
    fun `a weekend override moves the budget on Saturday only`() {
        val apps = listOf(app("a", 30, weekendLimitMinutes = 120))

        assertEquals(30 * 60L, dailyBudget(apps, emptyMap(), weekday)!!.budgetSeconds)
        assertEquals(120 * 60L, dailyBudget(apps, emptyMap(), weekend)!!.budgetSeconds)
    }

    /**
     * The reported bug: summing raw limits made an unlimited app contribute −60 seconds, which sailed
     * past the caller's `budget == 0` guard and reported "0% left" for a day with plenty to spare.
     */
    @Test
    fun `an uncapped app is left out rather than counted as negative budget`() {
        val budget = dailyBudget(
            listOf(app("capped", 60), app("free", UNLIMITED_MINUTES)),
            mapOf(usage("capped", 15), usage("free", 300)),
            weekday,
        )!!

        assertEquals("only the capped app's budget", 60 * 60L, budget.budgetSeconds)
        assertEquals("the uncapped app's time is not charged to it", 15 * 60L, budget.usedSeconds)
        assertEquals(75, budget.leftPercent)
    }

    @Test
    fun `an unlimited weekend drops the app from Saturday's budget only`() {
        val apps = listOf(app("a", 30, weekendLimitMinutes = UNLIMITED_MINUTES))

        assertEquals(30 * 60L, dailyBudget(apps, emptyMap(), weekday)!!.budgetSeconds)
        assertNull(dailyBudget(apps, emptyMap(), weekend))
    }

    @Test
    fun `nothing capped is no budget to report, not a zero one`() {
        assertNull(dailyBudget(emptyList(), emptyMap(), weekday))
        assertNull(dailyBudget(listOf(app("a", UNLIMITED_MINUTES)), emptyMap(), weekday))
    }

    @Test
    fun `earned time extends the allowance and its denominator`() {
        val budget = dailyBudget(
            listOf(app("a", 60)),
            mapOf(usage("a", 60, earnedMinutes = 60)),
            weekday,
        )!!

        assertEquals(60 * 60L, budget.remainingSeconds)
        assertEquals("half of a 120-minute allowance", 50, budget.leftPercent)
    }

    @Test
    fun `remaining floors at zero and the ring fills rather than overflowing`() {
        val budget = dailyBudget(listOf(app("a", 10)), mapOf(usage("a", 45)), weekday)!!

        assertEquals(0L, budget.remainingSeconds)
        assertEquals(0, budget.leftPercent)
        assertEquals(1f, budget.usedFraction, 0f)
    }

    @Test
    fun `an untouched budget is wholly unspent`() {
        val budget = dailyBudget(listOf(app("a", 60)), emptyMap(), weekday)!!

        assertEquals(100, budget.leftPercent)
        assertEquals(0f, budget.usedFraction, 0f)
    }
}
