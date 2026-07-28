package com.example.unpawse.data.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class CaptureRetentionTest {

    private val now = TimeUnit.DAYS.toMillis(1_000)

    @Test
    fun `the cutoff is the window back from now`() {
        assertEquals(now - TimeUnit.DAYS.toMillis(30), CaptureRetention.cutoff(now, 30))
        assertEquals(now - TimeUnit.DAYS.toMillis(7), CaptureRetention.cutoff(now, 7))
    }

    /**
     * "Keep forever" has to make every capture newer than the cutoff, so the purge finds nothing and
     * the Gallery hides nothing. Anything short of [Long.MIN_VALUE] would still expire old photos.
     */
    @Test
    fun `keep forever admits every capture`() {
        val cutoff = CaptureRetention.cutoff(now, CaptureRetention.KEEP_FOREVER)

        assertEquals(Long.MIN_VALUE, cutoff)
        assertTrue("the oldest conceivable capture survives", 0L >= cutoff)
    }

    @Test
    fun `a negative window is treated as keep forever rather than expiring everything`() {
        assertEquals(Long.MIN_VALUE, CaptureRetention.cutoff(now, -5))
    }

    @Test
    fun `labels read as the picker shows them`() {
        assertEquals("7 days", CaptureRetention.label(7))
        assertEquals("30 days", CaptureRetention.label(30))
        assertEquals("Keep forever", CaptureRetention.label(CaptureRetention.KEEP_FOREVER))
    }

    @Test
    fun `the default window is one of the offered choices`() {
        assertTrue(CaptureRetention.DEFAULT_WINDOW_DAYS in CaptureRetention.WINDOW_CHOICES)
    }
}
