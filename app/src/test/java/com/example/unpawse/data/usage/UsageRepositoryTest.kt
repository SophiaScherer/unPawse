package com.example.unpawse.data.usage

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.time.Duration.Companion.minutes

/**
 * Exercises [UsageRepository] against an in-memory fake DAO, with an injected clock so the daily
 * rollover is deterministic.
 */
class UsageRepositoryTest {

    private val dao = FakeUsageDao()
    private var today = LocalDate.of(2026, 7, 15)
    private val repo = UsageRepository(dao, today = { today })

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
}
