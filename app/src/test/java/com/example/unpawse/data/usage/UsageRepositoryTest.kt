package com.example.unpawse.data.usage

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.time.Duration.Companion.minutes

/**
 * Exercises [UsageRepository] against an in-memory fake DAO, with an injected clock so the daily
 * rollover is deterministic.
 */
class UsageRepositoryTest {

    private val dao = FakeUsageDao()
    private var today = LocalDate.of(2026, 7, 15)
    private var nowMillis = 1_000_000_000L
    private val repo = UsageRepository(dao, today = { today }, now = { nowMillis })

    @Test
    fun `usage accrues against the daily limit`() = runBlocking {
        repo.setLimit("com.ig", "Instagram", dailyLimitMinutes = 10)
        repo.addUsage("com.ig", 4.minutes)

        assertEquals(6, repo.remainingMinutes("com.ig"))
        assertFalse(repo.isLimitReached("com.ig"))
    }

    @Test
    fun `earned minutes extend the budget`() = runBlocking {
        repo.setLimit("com.ig", "Instagram", dailyLimitMinutes = 10)
        repo.addUsage("com.ig", 10.minutes)
        assertTrue(repo.isLimitReached("com.ig"))

        repo.addEarnedMinutes("com.ig", 5)

        assertFalse(repo.isLimitReached("com.ig"))
        assertEquals(5, repo.remainingMinutes("com.ig"))
    }

    @Test
    fun `the earning cap resets with the new day`() = runBlocking {
        repo.setLimit("com.ig", "Instagram", dailyLimitMinutes = 10)
        repo.tryEarnMinutes("com.ig", DAILY_EARNED_CAP_MINUTES)
        assertEquals(0, repo.earnableMinutes("com.ig"))

        today = today.plusDays(1)

        assertEquals(DAILY_EARNED_CAP_MINUTES, repo.earnableMinutes("com.ig"))
        assertEquals(
            RewardDecision.Granted(15),
            repo.tryEarnMinutes("com.ig", 15),
        )
    }

    /**
     * The cooldown stamp lives on the day's row, so a new day starts clean without a reset job —
     * even for a process that has been running since yesterday and never restarted the clock.
     */
    @Test
    fun `the cooldown does not carry across midnight`() = runBlocking {
        repo.setLimit("com.ig", "Instagram", dailyLimitMinutes = 10)
        repo.tryEarnMinutes("com.ig", 15)

        today = today.plusDays(1)
        nowMillis += 60_000L // one minute later in wall-clock terms

        assertEquals(RewardDecision.Granted(15), repo.tryEarnMinutes("com.ig", 15))
    }

    @Test
    fun `a new day resets usage`() = runBlocking {
        repo.setLimit("com.ig", "Instagram", dailyLimitMinutes = 10)
        repo.addUsage("com.ig", 10.minutes)
        assertTrue(repo.isLimitReached("com.ig"))

        today = today.plusDays(1)

        assertEquals(10, repo.remainingMinutes("com.ig"))
        assertFalse(repo.isLimitReached("com.ig"))
    }

    @Test
    fun `unmonitored or disabled apps report null remaining`() = runBlocking {
        assertNull(repo.remainingMinutes("com.unknown"))
        assertFalse(repo.isLimitReached("com.unknown"))

        repo.setLimit("com.ig", "Instagram", dailyLimitMinutes = 10, enabled = false)
        assertNull(repo.remainingMinutes("com.ig"))
        assertFalse(repo.isLimitReached("com.ig"))
    }

    // --- Weekday / weekend split ----------------------------------------------------------------
    //
    // `with(DayOfWeek)` moves within the same ISO week, so these don't depend on which weekday the
    // pinned date above happens to be.

    @Test
    fun `without an override the weekend uses the everyday budget`() = runBlocking {
        repo.setLimit("com.ig", "Instagram", dailyLimitMinutes = 10)
        today = today.with(DayOfWeek.SATURDAY)
        repo.addUsage("com.ig", 4.minutes)

        assertEquals(6, repo.remainingMinutes("com.ig"))
    }

    @Test
    fun `a weekend override applies only at the weekend`() = runBlocking {
        repo.setLimit("com.ig", "Instagram", dailyLimitMinutes = 10)
        repo.setWeekendLimit("com.ig", 60)

        today = today.with(DayOfWeek.SATURDAY)
        assertEquals(60, repo.limitMinutesToday("com.ig"))

        today = today.with(DayOfWeek.WEDNESDAY)
        assertEquals(10, repo.limitMinutesToday("com.ig"))
    }

    @Test
    fun `an unlimited weekend never blocks while weekdays still do`() = runBlocking {
        repo.setLimit("com.ig", "Instagram", dailyLimitMinutes = 10)
        repo.setWeekendLimit("com.ig", UNLIMITED_MINUTES)

        today = today.with(DayOfWeek.SUNDAY)
        repo.addUsage("com.ig", 300.minutes)
        assertFalse(repo.isLimitReached("com.ig"))
        // Nothing to count down, so nothing to warn about either.
        assertNull(repo.remainingMinutes("com.ig"))

        today = today.with(DayOfWeek.MONDAY)
        repo.addUsage("com.ig", 11.minutes)
        assertTrue(repo.isLimitReached("com.ig"))
    }

    @Test
    fun `clearing the override puts the weekend back on the everyday budget`() = runBlocking {
        repo.setLimit("com.ig", "Instagram", dailyLimitMinutes = 10)
        repo.setWeekendLimit("com.ig", UNLIMITED_MINUTES)
        today = today.with(DayOfWeek.SATURDAY)

        repo.setWeekendLimit("com.ig", null)

        assertEquals(10, repo.limitMinutesToday("com.ig"))
    }

    @Test
    fun `changing the daily limit keeps an existing weekend override`() = runBlocking {
        repo.setLimit("com.ig", "Instagram", dailyLimitMinutes = 10)
        repo.setWeekendLimit("com.ig", 60)

        // The stepper and the monitor switch both go through setLimit; neither should reset it.
        repo.setLimit("com.ig", "Instagram", dailyLimitMinutes = 25)
        repo.setEnabled("com.ig", enabled = true)

        today = today.with(DayOfWeek.SATURDAY)
        assertEquals(60, repo.limitMinutesToday("com.ig"))
        today = today.with(DayOfWeek.TUESDAY)
        assertEquals(25, repo.limitMinutesToday("com.ig"))
    }

    // --- Prevented count --------------------------------------------------------------------------

    private suspend fun blockedCount(packageName: String): Int? =
        dao.usageFor(packageName, today.toString())?.blockedCount

    @Test
    fun `the first block of the day creates the row`() = runBlocking {
        repo.setLimit("com.ig", "Instagram", dailyLimitMinutes = 10)

        repo.recordBlock("com.ig")

        assertEquals(1, blockedCount("com.ig"))
    }

    @Test
    fun `further blocks increment the existing row`() = runBlocking {
        repo.setLimit("com.ig", "Instagram", dailyLimitMinutes = 10)
        repo.addUsage("com.ig", 5.minutes)

        repeat(3) { repo.recordBlock("com.ig") }

        assertEquals(3, blockedCount("com.ig"))
        // The counter rides on the usage row without disturbing it.
        assertEquals(300L, dao.usageFor("com.ig", today.toString())?.usedSeconds)
    }

    @Test
    fun `the count resets with the new day`() = runBlocking {
        repo.setLimit("com.ig", "Instagram", dailyLimitMinutes = 10)
        repo.recordBlock("com.ig")
        repo.recordBlock("com.ig")

        today = today.plusDays(1)

        assertNull(blockedCount("com.ig"))
        repo.recordBlock("com.ig")
        assertEquals(1, blockedCount("com.ig"))
    }

    // --- Category ---------------------------------------------------------------------------------

    private suspend fun categoryOf(packageName: String): AppCategory =
        repo.monitoredApps().single { it.packageName == packageName }.category

    @Test
    fun `an app nobody has classified reads as Other`() = runBlocking {
        repo.setLimit("com.ig", "Instagram", dailyLimitMinutes = 10)

        assertEquals(AppCategory.OTHER, categoryOf("com.ig"))
    }

    @Test
    fun `first enable seeds the platform's guess`() = runBlocking {
        repo.setLimit("com.ig", "Instagram", dailyLimitMinutes = 10, defaultCategory = AppCategory.SOCIAL)

        assertEquals(AppCategory.SOCIAL, categoryOf("com.ig"))
    }

    @Test
    fun `setCategory overrides the seeded guess`() = runBlocking {
        repo.setLimit("com.ig", "Instagram", dailyLimitMinutes = 10, defaultCategory = AppCategory.SOCIAL)

        repo.setCategory("com.ig", AppCategory.PRODUCTIVITY)

        assertEquals(AppCategory.PRODUCTIVITY, categoryOf("com.ig"))
    }

    /**
     * The stepper and the monitor switch both go through [UsageRepository.setLimit], and both pass a
     * `defaultCategory` taken from the picker row. A stale default must not overwrite a choice the
     * user already made — same guarantee the weekend override has.
     */
    @Test
    fun `a later setLimit cannot overwrite a chosen category`() = runBlocking {
        repo.setLimit("com.ig", "Instagram", dailyLimitMinutes = 10)
        repo.setCategory("com.ig", AppCategory.PRODUCTIVITY)

        repo.setLimit("com.ig", "Instagram", dailyLimitMinutes = 25, defaultCategory = AppCategory.SOCIAL)
        repo.setLimit("com.ig", "Instagram", dailyLimitMinutes = 25)

        assertEquals(AppCategory.PRODUCTIVITY, categoryOf("com.ig"))
    }
}
