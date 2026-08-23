package com.example.unpawse.ui.block

import com.example.unpawse.data.usage.DAILY_EARNED_CAP_MINUTES
import com.example.unpawse.data.usage.RewardDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The overlay is where the reward bounds finally get said out loud, so what it says has to match
 * what the credit path will actually do.
 */
class BlockCopyTest {

    private fun terms(
        decision: RewardDecision,
        grantMinutes: Int = 15,
        earnableMinutes: Int = DAILY_EARNED_CAP_MINUTES,
    ) = rewardTerms("Chrome", decision, grantMinutes, earnableMinutes)

    @Test
    fun `a payable block states the grant and what is left of today's allowance`() {
        val terms = terms(RewardDecision.Granted(15))

        assertNotNull(terms)
        assertEquals("+15m", terms!!.grantValue)
        // The whole cap, formatted the way every other duration in the app is.
        assertEquals("1h", terms.allowanceValue)
        assertNull("nothing is being waited on", terms.cooldownNote)
    }

    /**
     * The cap trims a grant rather than refusing it, so the pill must report the trimmed figure —
     * promising "+15m" when the last of the allowance is 10 would be a number the camera contradicts.
     */
    @Test
    fun `a grant trimmed by the cap reports what the cat will really pay`() {
        val terms = terms(RewardDecision.Granted(10), grantMinutes = 15, earnableMinutes = 10)

        assertEquals("+10m", terms!!.grantValue)
        assertEquals("10m", terms.allowanceValue)
    }

    /** No terms is how the service is told to drop the camera button and arm no session. */
    @Test
    fun `a spent allowance has no terms to state`() {
        assertNull(terms(RewardDecision.Capped(DAILY_EARNED_CAP_MINUTES), earnableMinutes = 0))
    }

    @Test
    fun `a cooldown names the app and how long is left, and still states the grant`() {
        val terms = terms(RewardDecision.CoolingDown(retrySeconds = 360))

        assertNotNull("a cooldown ends; the escape is still real", terms)
        assertEquals("+15m", terms!!.grantValue)
        assertEquals("Chrome can earn again in 6 minutes.", terms.cooldownNote)
    }

    @Test
    fun `a sub-minute wait is not rounded away`() {
        val terms = terms(RewardDecision.CoolingDown(retrySeconds = 20))

        assertTrue(terms!!.cooldownNote!!.contains("under a minute"))
    }

    /** Hours are formatted like every other duration in the app, not as "45m"/"90m" raw minutes. */
    @Test
    fun `a part-hour allowance reads in both units`() {
        assertEquals("1h 30m", terms(RewardDecision.Granted(15), earnableMinutes = 90)!!.allowanceValue)
        assertEquals("45m", terms(RewardDecision.Granted(15), earnableMinutes = 45)!!.allowanceValue)
    }
}
