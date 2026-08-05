package com.example.unpawse.data.usage

import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure cap arithmetic — no Room, no coroutines, mirroring [UsageMathTest]. */
class RewardPolicyTest {

    private fun minutesToSeconds(minutes: Int) = minutes.toLong() * 60

    @Test
    fun `a fresh day grants the full request`() {
        assertEquals(
            RewardDecision.Granted(15),
            decideReward(requestedMinutes = 15, earnedSecondsToday = 0),
        )
    }

    @Test
    fun `grants keep landing while the cap has room`() {
        assertEquals(
            RewardDecision.Granted(15),
            decideReward(15, earnedSecondsToday = minutesToSeconds(45)),
        )
    }

    @Test
    fun `the last cat grants only what is left of the cap`() {
        assertEquals(
            RewardDecision.Granted(5),
            decideReward(15, earnedSecondsToday = minutesToSeconds(55)),
        )
    }

    @Test
    fun `a request at exactly the cap is refused`() {
        assertEquals(
            RewardDecision.Capped(DAILY_EARNED_CAP_MINUTES),
            decideReward(15, earnedSecondsToday = minutesToSeconds(DAILY_EARNED_CAP_MINUTES)),
        )
    }

    @Test
    fun `an over-cap total stays refused rather than going negative`() {
        assertEquals(
            RewardDecision.Capped(DAILY_EARNED_CAP_MINUTES),
            decideReward(15, earnedSecondsToday = minutesToSeconds(90)),
        )
    }

    @Test
    fun `a non-positive request earns nothing`() {
        assertEquals(RewardDecision.Capped(DAILY_EARNED_CAP_MINUTES), decideReward(0, 0))
        assertEquals(RewardDecision.Capped(DAILY_EARNED_CAP_MINUTES), decideReward(-5, 0))
    }

    @Test
    fun `partial seconds never round a grant up past the cap`() {
        // 59m30s earned: only 30s of headroom, which is less than a whole minute.
        assertEquals(
            RewardDecision.Capped(DAILY_EARNED_CAP_MINUTES),
            decideReward(15, earnedSecondsToday = minutesToSeconds(59) + 30),
        )
    }

    @Test
    fun `earnable minutes count down with each grant`() {
        assertEquals(60, earnableMinutes(earnedSecondsToday = 0))
        assertEquals(45, earnableMinutes(minutesToSeconds(15)))
        assertEquals(0, earnableMinutes(minutesToSeconds(60)))
        assertEquals(0, earnableMinutes(minutesToSeconds(75)))
    }

    @Test
    fun `the cap is a parameter, so a different ceiling is expressible`() {
        assertEquals(
            RewardDecision.Granted(10),
            decideReward(15, earnedSecondsToday = minutesToSeconds(20), dailyCapMinutes = 30),
        )
    }
}
