package com.example.unpawse.ui.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The wording of a refusal is the only thing the user sees when a cat doesn't pay out, so each
 * outcome must say something different — and something true.
 */
class CameraHintsTest {

    @Test
    fun `a casual capture just confirms the save`() {
        assertEquals(
            "Purrfect! Saved to your gallery.",
            savedHint(RewardOutcome.NoActiveBlock),
        )
    }

    @Test
    fun `a paid capture names the app and the minutes`() {
        assertEquals(
            "Purrfect! +15 min of Instagram.",
            savedHint(RewardOutcome.Earned(appLabel = "Instagram", minutes = 15)),
        )
    }

    @Test
    fun `a capped capture says the photo was kept and why it bought nothing`() {
        val hint = savedHint(RewardOutcome.DailyCapReached(appLabel = "Instagram", capMinutes = 60))

        assertTrue("should confirm the save: $hint", hint.startsWith("Saved!"))
        assertTrue("should name the app: $hint", hint.contains("Instagram"))
        assertTrue("should name the cap: $hint", hint.contains("60"))
        assertTrue("should say when it lifts: $hint", hint.contains("today"))
    }

    @Test
    fun `a cooling-down capture says how long is left`() {
        assertEquals(
            "Saved! Instagram can earn again in 4 minutes.",
            savedHint(RewardOutcome.CoolingDown(appLabel = "Instagram", retrySeconds = 240)),
        )
    }

    @Test
    fun `one minute is singular`() {
        assertEquals("1 minute", retryText(60))
    }

    @Test
    fun `a sub-minute wait is not rounded down to zero`() {
        assertEquals("under a minute", retryText(1))
        assertEquals("under a minute", retryText(59))
    }

    @Test
    fun `a part-minute wait rounds up, so the wait is never under-promised`() {
        assertEquals("2 minutes", retryText(61))
        assertEquals("5 minutes", retryText(241))
    }

    @Test
    fun `a lapsed wait reads as a moment rather than a negative`() {
        assertEquals("a moment", retryText(0))
        assertEquals("a moment", retryText(-5))
    }
}
