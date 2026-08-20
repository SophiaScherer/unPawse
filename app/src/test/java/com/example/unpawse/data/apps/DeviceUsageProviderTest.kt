package com.example.unpawse.data.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Covers the pure averaging; the UsageStatsManager query itself needs a device. */
class DeviceUsageProviderTest {

    private val hour = 60L * 60 * 1000

    @Test
    fun `a total is spread across the window`() {
        // 7 hours over 7 days is an hour a day.
        val averages = averageSecondsPerDay(mapOf("com.ig" to 7 * hour), days = 7)

        assertEquals(3600L, averages.getValue("com.ig"))
    }

    @Test
    fun `every package is averaged over the same window`() {
        val averages = averageSecondsPerDay(
            mapOf("com.ig" to 14 * hour, "com.tiktok" to 7 * hour),
            days = 7,
        )

        assertEquals(7200L, averages.getValue("com.ig"))
        assertEquals(3600L, averages.getValue("com.tiktok"))
    }

    @Test
    fun `a package with no foreground time averages to a real zero`() {
        // Not absent and not null: the platform measured it, and the answer was none.
        val averages = averageSecondsPerDay(mapOf("com.ig" to 0L), days = 7)

        assertEquals(0L, averages.getValue("com.ig"))
        assertTrue(averages.containsKey("com.ig"))
    }

    @Test
    fun `averages truncate rather than round`() {
        // 13 seconds over 7 days is 1.857s/day; the app floors durations everywhere else.
        val averages = averageSecondsPerDay(mapOf("com.ig" to 13_000L), days = 7)

        assertEquals(1L, averages.getValue("com.ig"))
    }

    @Test
    fun `a sub-second average floors to zero rather than disappearing`() {
        val averages = averageSecondsPerDay(mapOf("com.ig" to 500L), days = 7)

        assertEquals(0L, averages.getValue("com.ig"))
    }

    @Test
    fun `a nonsensical negative total clamps to zero`() {
        // A device whose clock has moved can report these; a negative average would sort the app
        // above ones that genuinely went unused.
        val averages = averageSecondsPerDay(mapOf("com.ig" to -5 * hour), days = 7)

        assertEquals(0L, averages.getValue("com.ig"))
    }

    @Test
    fun `a zero or negative window does not divide by zero`() {
        assertEquals(3600L, averageSecondsPerDay(mapOf("com.ig" to hour), days = 0).getValue("com.ig"))
        assertEquals(3600L, averageSecondsPerDay(mapOf("com.ig" to hour), days = -3).getValue("com.ig"))
    }

    @Test
    fun `no usage rows stay no usage rows`() {
        assertTrue(averageSecondsPerDay(emptyMap(), days = RECENT_DAYS).isEmpty())
    }
}
