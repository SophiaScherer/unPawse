package com.example.unpawse.service

import com.example.unpawse.data.settings.SettingsRepository
import com.example.unpawse.data.usage.FakeUsageDao
import com.example.unpawse.data.usage.UsageRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.time.Duration.Companion.minutes

/**
 * The product guarantee the whole app exists for: photograph a cat, get back into the app.
 *
 * The grant is now a Settings value rather than a hardcoded one, so these run against
 * [BONUS_MINUTES_PER_CAT] — its shipped default — plus a case at a user-chosen value, pinning that
 * the unblock behaviour holds for whatever the stepper is set to and not just for 15.
 */
class RewardLoopTest {

    private val dao = FakeUsageDao()
    private val repo = UsageRepository(dao, today = { LocalDate.of(2026, 7, 15) })

    @Test
    fun `a verified cat unblocks a maxed-out app`() = runBlocking {
        repo.setLimit("com.ig", "Instagram", dailyLimitMinutes = 15)
        repo.addUsage("com.ig", 15.minutes)
        assertTrue("precondition: app should be blocked", repo.isLimitReached("com.ig"))

        repo.addEarnedMinutes("com.ig", BONUS_MINUTES_PER_CAT)

        assertFalse(repo.isLimitReached("com.ig"))
        assertEquals(BONUS_MINUTES_PER_CAT, repo.remainingMinutes("com.ig"))
    }

    @Test
    fun `earned time is spent like any other, and re-blocks when used up`() = runBlocking {
        repo.setLimit("com.ig", "Instagram", dailyLimitMinutes = 15)
        repo.addUsage("com.ig", 15.minutes)
        repo.addEarnedMinutes("com.ig", BONUS_MINUTES_PER_CAT)

        // Burn exactly the bonus back down.
        repo.addUsage("com.ig", BONUS_MINUTES_PER_CAT.minutes)

        assertTrue(repo.isLimitReached("com.ig"))
    }

    @Test
    fun `each cat stacks another grant`() = runBlocking {
        repo.setLimit("com.ig", "Instagram", dailyLimitMinutes = 15)
        repo.addUsage("com.ig", 15.minutes)

        repo.addEarnedMinutes("com.ig", BONUS_MINUTES_PER_CAT)
        repo.addEarnedMinutes("com.ig", BONUS_MINUTES_PER_CAT)

        assertEquals(BONUS_MINUTES_PER_CAT * 2, repo.remainingMinutes("com.ig"))
    }

    @Test
    fun `a custom grant unblocks by exactly what the user chose`() = runBlocking {
        val customGrant = 30
        repo.setLimit("com.ig", "Instagram", dailyLimitMinutes = 15)
        repo.addUsage("com.ig", 15.minutes)

        repo.addEarnedMinutes("com.ig", customGrant)

        assertFalse(repo.isLimitReached("com.ig"))
        assertEquals(customGrant, repo.remainingMinutes("com.ig"))
    }

    @Test
    fun `the smallest grant still lifts the block`() = runBlocking {
        repo.setLimit("com.ig", "Instagram", dailyLimitMinutes = 15)
        repo.addUsage("com.ig", 15.minutes)

        repo.addEarnedMinutes("com.ig", SettingsRepository.MIN_EARNED_MINUTES_PER_CAT)

        assertFalse(repo.isLimitReached("com.ig"))
    }

    @Test
    fun `bonus minutes are scoped to the blocked app`() = runBlocking {
        repo.setLimit("com.ig", "Instagram", dailyLimitMinutes = 15)
        repo.setLimit("com.tiktok", "TikTok", dailyLimitMinutes = 15)
        repo.addUsage("com.ig", 15.minutes)
        repo.addUsage("com.tiktok", 15.minutes)

        repo.addEarnedMinutes("com.ig", BONUS_MINUTES_PER_CAT)

        assertFalse("the app we paid for is unblocked", repo.isLimitReached("com.ig"))
        assertTrue("other apps stay blocked", repo.isLimitReached("com.tiktok"))
    }
}
