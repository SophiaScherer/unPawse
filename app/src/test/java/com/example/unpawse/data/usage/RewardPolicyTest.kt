package com.example.unpawse.data.usage

import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure cap + cooldown arithmetic — no Room, no coroutines, mirroring [UsageMathTest]. */
class RewardPolicyTest {

    private val cooldownMillis = REWARD_COOLDOWN_MINUTES * 60_000L

    /** An arbitrary "now" far enough from 0 that a cooldown window can sit before it. */
    private val now = 1_000_000_000L

    private fun minutesToSeconds(minutes: Int) = minutes.toLong() * 60

    /** Defaults to "never earned today", so cap cases aren't accidentally testing the cooldown. */
    private fun decide(
        requestedMinutes: Int,
        earnedSecondsToday: Long,
        lastEarnedAtMillis: Long = 0,
        nowMillis: Long = now,
        dailyCapMinutes: Int = DAILY_EARNED_CAP_MINUTES,
    ) = decideReward(
        requestedMinutes = requestedMinutes,
        earnedSecondsToday = earnedSecondsToday,
        lastEarnedAtMillis = lastEarnedAtMillis,
        nowMillis = nowMillis,
        dailyCapMinutes = dailyCapMinutes,
    )

    // --- Daily cap ----------------------------------------------------------------------------

    @Test
    fun `a fresh day grants the full request`() {
        assertEquals(RewardDecision.Granted(15), decide(15, earnedSecondsToday = 0))
    }

    @Test
    fun `grants keep landing while the cap has room`() {
        assertEquals(RewardDecision.Granted(15), decide(15, minutesToSeconds(45)))
    }

    @Test
    fun `the last cat grants only what is left of the cap`() {
        assertEquals(RewardDecision.Granted(5), decide(15, minutesToSeconds(55)))
    }

    @Test
    fun `a request at exactly the cap is refused`() {
        assertEquals(
            RewardDecision.Capped(DAILY_EARNED_CAP_MINUTES),
            decide(15, minutesToSeconds(DAILY_EARNED_CAP_MINUTES)),
        )
    }

    @Test
    fun `an over-cap total stays refused rather than going negative`() {
        assertEquals(
            RewardDecision.Capped(DAILY_EARNED_CAP_MINUTES),
            decide(15, minutesToSeconds(90)),
        )
    }

    @Test
    fun `a non-positive request earns nothing`() {
        assertEquals(RewardDecision.Capped(DAILY_EARNED_CAP_MINUTES), decide(0, 0))
        assertEquals(RewardDecision.Capped(DAILY_EARNED_CAP_MINUTES), decide(-5, 0))
    }

    @Test
    fun `partial seconds never round a grant up past the cap`() {
        // 59m30s earned: only 30s of headroom, which is less than a whole minute.
        assertEquals(
            RewardDecision.Capped(DAILY_EARNED_CAP_MINUTES),
            decide(15, minutesToSeconds(59) + 30),
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
            decide(15, minutesToSeconds(20), dailyCapMinutes = 30),
        )
    }

    // --- Cooldown -----------------------------------------------------------------------------

    @Test
    fun `the first cat of the day never waits`() {
        assertEquals(
            RewardDecision.Granted(15),
            decide(15, earnedSecondsToday = 0, lastEarnedAtMillis = 0),
        )
    }

    @Test
    fun `a second cat inside the cooldown is refused`() {
        val oneMinuteAgo = now - 60_000L

        assertEquals(
            RewardDecision.CoolingDown(retrySeconds = (REWARD_COOLDOWN_MINUTES - 1) * 60L),
            decide(15, minutesToSeconds(15), lastEarnedAtMillis = oneMinuteAgo),
        )
    }

    @Test
    fun `the cooldown ends exactly on the boundary`() {
        assertEquals(
            RewardDecision.Granted(15),
            decide(15, minutesToSeconds(15), lastEarnedAtMillis = now - cooldownMillis),
        )
    }

    @Test
    fun `one millisecond short of the boundary still waits`() {
        assertEquals(
            RewardDecision.CoolingDown(retrySeconds = 1),
            decide(15, minutesToSeconds(15), lastEarnedAtMillis = now - cooldownMillis + 1),
        )
    }

    @Test
    fun `retry seconds round up, so a sub-second wait is not reported as zero`() {
        assertEquals(
            RewardDecision.CoolingDown(retrySeconds = 1),
            decide(15, minutesToSeconds(15), lastEarnedAtMillis = now - cooldownMillis + 100),
        )
    }

    @Test
    fun `the cap is reported ahead of the cooldown when both apply`() {
        // Waiting out the cooldown would only end in a refusal, so say the more final thing.
        assertEquals(
            RewardDecision.Capped(DAILY_EARNED_CAP_MINUTES),
            decide(
                15,
                minutesToSeconds(DAILY_EARNED_CAP_MINUTES),
                lastEarnedAtMillis = now - 60_000L,
            ),
        )
    }

    @Test
    fun `a clock that moved backwards does not lock the user out`() {
        // Skew or a timezone change can put the stamp in the future; read that as elapsed.
        assertEquals(
            RewardDecision.Granted(15),
            decide(15, minutesToSeconds(15), lastEarnedAtMillis = now + cooldownMillis),
        )
    }
}
