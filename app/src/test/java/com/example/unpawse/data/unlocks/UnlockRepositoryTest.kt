package com.example.unpawse.data.unlocks

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Exercises [UnlockRepository] against an in-memory fake DAO, with an injected clock so the daily
 * rollover is deterministic — same shape as `UsageRepositoryTest`.
 */
class UnlockRepositoryTest {

    private val dao = FakeUnlockDao()
    private var today = LocalDate.of(2026, 7, 15)
    private val repo = UnlockRepository(dao, today = { today })

    private suspend fun countOn(date: LocalDate): Int? =
        repo.allUnlocks().firstOrNull { it.date == date.toString() }?.unlockCount

    @Test
    fun `the first unlock of the day creates the row`() = runBlocking {
        repo.recordUnlock()

        assertEquals(1, countOn(today))
    }

    @Test
    fun `further unlocks increment the same row`() = runBlocking {
        repeat(4) { repo.recordUnlock() }

        assertEquals(4, countOn(today))
        assertEquals("one row per day, not one per unlock", 1, repo.allUnlocks().size)
    }

    @Test
    fun `the count resets with the new day`() = runBlocking {
        repeat(3) { repo.recordUnlock() }

        today = today.plusDays(1)
        repo.recordUnlock()

        assertEquals(3, countOn(today.minusDays(1)))
        assertEquals(1, countOn(today))
    }

    @Test
    fun `the recent window is inclusive at both ends`() = runBlocking {
        // Seed three days: today, 6 days ago (the window's first day), 7 days ago (just outside).
        repo.recordUnlock()
        today = today.minusDays(6)
        repo.recordUnlock()
        today = today.minusDays(1)
        repo.recordUnlock()
        today = today.plusDays(7)

        val window = repo.observeRecentUnlocks(days = 7).first().map { it.date }

        assertEquals(2, window.size)
        assertTrue(window.contains(today.toString()))
        assertTrue(window.contains(today.minusDays(6).toString()))
    }

    @Test
    fun `clearing drops the whole history`() = runBlocking {
        repeat(3) { repo.recordUnlock() }

        repo.clearAll()

        assertTrue(repo.allUnlocks().isEmpty())
    }
}
