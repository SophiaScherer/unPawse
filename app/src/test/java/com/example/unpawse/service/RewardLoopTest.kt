package com.example.unpawse.service

import com.example.unpawse.data.settings.SettingsRepository
import com.example.unpawse.data.usage.DAILY_EARNED_CAP_MINUTES
import com.example.unpawse.data.usage.FakeUsageDao
import com.example.unpawse.data.usage.REWARD_COOLDOWN_MINUTES
import com.example.unpawse.data.usage.RewardDecision
import com.example.unpawse.data.usage.UsageRepository
import com.example.unpawse.ui.block.BlockUiState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.time.Duration.Companion.minutes

/**
 * The product guarantee the whole app exists for: photograph a cat, get back into the app — and
 * the bound that keeps that guarantee from swallowing the limit it's an exception to.
 *
 * The grant is a Settings value rather than a hardcoded one, so these run against
 * [BONUS_MINUTES_PER_CAT] — its shipped default — plus a case at a user-chosen value, pinning that
 * the unblock behaviour holds for whatever the stepper is set to and not just for 15.
 *
 * Reward-loop cases go through `tryEarnMinutes`, the capped path the camera actually uses;
 * `addEarnedMinutes` appears only where a test needs to seed earned time directly.
 *
 * Consecutive grants advance [nowMillis] past the cooldown via [waitOutCooldown], so a case about
 * the *cap* isn't quietly asserting the cooldown instead.
 */
class RewardLoopTest {

    private val dao = FakeUsageDao()
    private var nowMillis = 1_000_000_000L
    private val repo = UsageRepository(
        dao,
        today = { LocalDate.of(2026, 7, 15) },
        now = { nowMillis },
    )

    private fun waitOutCooldown() {
        nowMillis += REWARD_COOLDOWN_MINUTES * 60_000L
    }

    @Test
    fun `a verified cat unblocks a maxed-out app`() = runBlocking {
        repo.setLimit("com.ig", "Instagram", dailyLimitMinutes = 15)
        repo.addUsage("com.ig", 15.minutes)
        assertTrue("precondition: app should be blocked", repo.isLimitReached("com.ig"))

        repo.tryEarnMinutes("com.ig", BONUS_MINUTES_PER_CAT)

        assertFalse(repo.isLimitReached("com.ig"))
        assertEquals(BONUS_MINUTES_PER_CAT, repo.remainingMinutes("com.ig"))
    }

    @Test
    fun `earned time is spent like any other, and re-blocks when used up`() = runBlocking {
        repo.setLimit("com.ig", "Instagram", dailyLimitMinutes = 15)
        repo.addUsage("com.ig", 15.minutes)
        repo.tryEarnMinutes("com.ig", BONUS_MINUTES_PER_CAT)

        // Burn exactly the bonus back down.
        repo.addUsage("com.ig", BONUS_MINUTES_PER_CAT.minutes)

        assertTrue(repo.isLimitReached("com.ig"))
    }

    @Test
    fun `grants stack up to the daily cap, then stop`() = runBlocking {
        repo.setLimit("com.ig", "Instagram", dailyLimitMinutes = 15)
        repo.addUsage("com.ig", 15.minutes)

        // Four cats at the default grant is exactly the cap.
        val catsToCap = DAILY_EARNED_CAP_MINUTES / BONUS_MINUTES_PER_CAT
        repeat(catsToCap) {
            assertTrue(
                "cat ${it + 1} should still pay out",
                repo.tryEarnMinutes("com.ig", BONUS_MINUTES_PER_CAT) is RewardDecision.Granted,
            )
            waitOutCooldown()
        }
        assertEquals(DAILY_EARNED_CAP_MINUTES, repo.remainingMinutes("com.ig"))

        // The fifth buys nothing, however many times it's tried and however long it waits.
        repeat(3) {
            assertEquals(
                RewardDecision.Capped(DAILY_EARNED_CAP_MINUTES),
                repo.tryEarnMinutes("com.ig", BONUS_MINUTES_PER_CAT),
            )
            waitOutCooldown()
        }
        assertEquals(DAILY_EARNED_CAP_MINUTES, repo.remainingMinutes("com.ig"))
    }

    @Test
    fun `the last cat grants only what is left of the cap`() = runBlocking {
        repo.setLimit("com.ig", "Instagram", dailyLimitMinutes = 15)
        repo.addUsage("com.ig", 15.minutes)

        // A 45-minute grant leaves 15 of the 60-minute cap; the next cat gets that, not 45.
        repo.tryEarnMinutes("com.ig", 45)
        waitOutCooldown()

        assertEquals(RewardDecision.Granted(15), repo.tryEarnMinutes("com.ig", 45))
        assertEquals(DAILY_EARNED_CAP_MINUTES, repo.remainingMinutes("com.ig"))
    }

    @Test
    fun `the cap is per app`() = runBlocking {
        repo.setLimit("com.ig", "Instagram", dailyLimitMinutes = 15)
        repo.setLimit("com.tiktok", "TikTok", dailyLimitMinutes = 15)
        repo.addUsage("com.ig", 15.minutes)
        repo.addUsage("com.tiktok", 15.minutes)

        repo.tryEarnMinutes("com.ig", DAILY_EARNED_CAP_MINUTES)

        assertEquals(0, repo.earnableMinutes("com.ig"))
        assertEquals(DAILY_EARNED_CAP_MINUTES, repo.earnableMinutes("com.tiktok"))
        assertEquals(
            RewardDecision.Granted(BONUS_MINUTES_PER_CAT),
            repo.tryEarnMinutes("com.tiktok", BONUS_MINUTES_PER_CAT),
        )
    }

    @Test
    fun `a second cat inside the cooldown earns nothing`() = runBlocking {
        repo.setLimit("com.ig", "Instagram", dailyLimitMinutes = 15)
        repo.addUsage("com.ig", 15.minutes)
        repo.tryEarnMinutes("com.ig", BONUS_MINUTES_PER_CAT)

        nowMillis += 60_000L // one minute later

        val decision = repo.tryEarnMinutes("com.ig", BONUS_MINUTES_PER_CAT)

        assertTrue("expected a cooldown, got $decision", decision is RewardDecision.CoolingDown)
        assertEquals(
            "the refused cat must not have been credited",
            BONUS_MINUTES_PER_CAT,
            repo.remainingMinutes("com.ig"),
        )
    }

    @Test
    fun `waiting out the cooldown earns again`() = runBlocking {
        repo.setLimit("com.ig", "Instagram", dailyLimitMinutes = 15)
        repo.addUsage("com.ig", 15.minutes)
        repo.tryEarnMinutes("com.ig", BONUS_MINUTES_PER_CAT)

        waitOutCooldown()

        assertEquals(
            RewardDecision.Granted(BONUS_MINUTES_PER_CAT),
            repo.tryEarnMinutes("com.ig", BONUS_MINUTES_PER_CAT),
        )
        assertEquals(BONUS_MINUTES_PER_CAT * 2, repo.remainingMinutes("com.ig"))
    }

    @Test
    fun `the cooldown is per app, so one blocked app does not gate another`() = runBlocking {
        repo.setLimit("com.ig", "Instagram", dailyLimitMinutes = 15)
        repo.setLimit("com.tiktok", "TikTok", dailyLimitMinutes = 15)
        repo.addUsage("com.ig", 15.minutes)
        repo.addUsage("com.tiktok", 15.minutes)

        repo.tryEarnMinutes("com.ig", BONUS_MINUTES_PER_CAT)

        assertEquals(
            RewardDecision.Granted(BONUS_MINUTES_PER_CAT),
            repo.tryEarnMinutes("com.tiktok", BONUS_MINUTES_PER_CAT),
        )
    }

    @Test
    fun `a capped app stays blocked once its earned time is spent`() = runBlocking {
        repo.setLimit("com.ig", "Instagram", dailyLimitMinutes = 15)
        repo.addUsage("com.ig", 15.minutes)
        repo.tryEarnMinutes("com.ig", DAILY_EARNED_CAP_MINUTES)

        repo.addUsage("com.ig", DAILY_EARNED_CAP_MINUTES.minutes)

        assertTrue(repo.isLimitReached("com.ig"))
        assertEquals(RewardDecision.Capped(DAILY_EARNED_CAP_MINUTES), repo.tryEarnMinutes("com.ig", 15))
        assertTrue("no cat can lift it again today", repo.isLimitReached("com.ig"))
    }

    @Test
    fun `a custom grant unblocks by exactly what the user chose`() = runBlocking {
        val customGrant = 30
        repo.setLimit("com.ig", "Instagram", dailyLimitMinutes = 15)
        repo.addUsage("com.ig", 15.minutes)

        repo.tryEarnMinutes("com.ig", customGrant)

        assertFalse(repo.isLimitReached("com.ig"))
        assertEquals(customGrant, repo.remainingMinutes("com.ig"))
    }

    @Test
    fun `the smallest grant still lifts the block`() = runBlocking {
        repo.setLimit("com.ig", "Instagram", dailyLimitMinutes = 15)
        repo.addUsage("com.ig", 15.minutes)

        repo.tryEarnMinutes("com.ig", SettingsRepository.MIN_EARNED_MINUTES_PER_CAT)

        assertFalse(repo.isLimitReached("com.ig"))
    }

    /**
     * The overlay's last honest state: with the allowance spent, offering "Open Camera" would only
     * lead to a refusal, so the button goes away rather than the user finding out the hard way.
     */
    @Test
    fun `the overlay stops offering the camera once the cap is spent`() = runBlocking {
        repo.setLimit("com.ig", "Instagram", dailyLimitMinutes = 15)
        repo.addUsage("com.ig", 15.minutes)

        assertTrue(repo.earnableMinutes("com.ig") > 0)
        assertTrue(BlockUiState.forApp("Instagram").showCamera)

        repo.tryEarnMinutes("com.ig", DAILY_EARNED_CAP_MINUTES)

        assertEquals(0, repo.earnableMinutes("com.ig"))
        assertFalse(BlockUiState.forAppOutOfRewards("Instagram").showCamera)
    }

    @Test
    fun `bonus minutes are scoped to the blocked app`() = runBlocking {
        repo.setLimit("com.ig", "Instagram", dailyLimitMinutes = 15)
        repo.setLimit("com.tiktok", "TikTok", dailyLimitMinutes = 15)
        repo.addUsage("com.ig", 15.minutes)
        repo.addUsage("com.tiktok", 15.minutes)

        repo.tryEarnMinutes("com.ig", BONUS_MINUTES_PER_CAT)

        assertFalse("the app we paid for is unblocked", repo.isLimitReached("com.ig"))
        assertTrue("other apps stay blocked", repo.isLimitReached("com.tiktok"))
    }
}
