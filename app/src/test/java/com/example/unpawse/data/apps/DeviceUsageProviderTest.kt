package com.example.unpawse.data.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** Covers the pure averaging and day boundaries; the UsageStatsManager query itself needs a device. */
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

    private val utc = ZoneId.of("UTC")

    @Test
    fun `the window ends on today and runs oldest first`() {
        val windows = dayWindows(LocalDate.of(2026, 8, 31), days = 3, zone = utc)

        assertEquals(listOf("2026-08-29", "2026-08-30", "2026-08-31"), windows.map { it.date })
    }

    @Test
    fun `each day spans exactly its own local midnights`() {
        val windows = dayWindows(LocalDate.of(2026, 8, 31), days = 2, zone = utc)

        // Half-open and butt-joined: one day ends where the next begins, so no second is counted
        // twice and none falls between the two queries.
        assertEquals(windows[0].endMillis, windows[1].beginMillis)
        assertEquals(DAY_MILLIS, windows[0].endMillis - windows[0].beginMillis)
    }

    @Test
    fun `boundaries follow the zone rather than UTC`() {
        val berlin = ZoneId.of("Europe/Berlin")
        val day = LocalDate.of(2026, 8, 31)

        val windows = dayWindows(day, days = 1, zone = berlin)

        // Berlin is UTC+2 in August, so its midnight is two hours before UTC's.
        val utcMidnight = dayWindows(day, days = 1, zone = utc).single().beginMillis
        assertEquals(utcMidnight - 2 * 60 * 60 * 1000, windows.single().beginMillis)
    }

    @Test
    fun `a spring-forward day starts at the first instant that exists`() {
        // Europe/Berlin has no 02-00 on 2026-03-29; midnight itself is real, and the day is 23h.
        val berlin = ZoneId.of("Europe/Berlin")

        val window = dayWindows(LocalDate.of(2026, 3, 29), days = 1, zone = berlin).single()

        assertEquals(DAY_MILLIS - 60 * 60 * 1000, window.endMillis - window.beginMillis)
    }

    @Test
    fun `a zero or negative window still asks for one day`() {
        // Mirrors averageSecondsPerDay's coercion: nothing downstream can divide by, or iterate, zero.
        assertEquals(1, dayWindows(LocalDate.of(2026, 8, 31), days = 0, zone = utc).size)
        assertEquals(1, dayWindows(LocalDate.of(2026, 8, 31), days = -3, zone = utc).size)
    }

    private companion object {
        const val DAY_MILLIS = 24L * 60 * 60 * 1000
    }
}
