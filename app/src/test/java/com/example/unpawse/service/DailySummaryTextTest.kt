package com.example.unpawse.service

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

class DailySummaryTextTest {

    private fun app(label: String, minutes: Int) = AppUsageSummary(label, minutes)

    @Test
    fun `a day with nothing on it says so rather than reporting a zero`() {
        assertEquals(
            "No time on your limited apps today.",
            buildDailySummary(apps = emptyList(), captureCount = 0),
        )
    }

    @Test
    fun `apps with no time today are not counted as apps used`() {
        val summary = buildDailySummary(
            apps = listOf(app("Instagram", 0), app("TikTok", 0)),
            captureCount = 0,
        )

        assertEquals("No time on your limited apps today.", summary)
    }

    @Test
    fun `a single app is named without a breakdown`() {
        assertEquals(
            "45m in Instagram.",
            buildDailySummary(apps = listOf(app("Instagram", 45)), captureCount = 0),
        )
    }

    @Test
    fun `several apps report the total and the biggest share`() {
        val summary = buildDailySummary(
            apps = listOf(app("TikTok", 20), app("Instagram", 45), app("Reddit", 15)),
            captureCount = 0,
        )

        assertEquals("1h 20m across 3 apps, most of it in Instagram (45m).", summary)
    }

    @Test
    fun `cats are added, and counted in the singular`() {
        assertEquals(
            "45m in Instagram. You photographed 1 cat.",
            buildDailySummary(apps = listOf(app("Instagram", 45)), captureCount = 1),
        )
        assertEquals(
            "45m in Instagram. You photographed 3 cats.",
            buildDailySummary(apps = listOf(app("Instagram", 45)), captureCount = 3),
        )
    }

    @Test
    fun `cats still get a mention on a day with no screen time`() {
        assertEquals(
            "No time on your limited apps today. You photographed 2 cats.",
            buildDailySummary(apps = emptyList(), captureCount = 2),
        )
    }

    // --- Scheduling -----------------------------------------------------------------------------

    private val zone = ZoneId.of("UTC")

    private fun millisAt(hour: Int, minute: Int = 0) =
        ZonedDateTime.of(2026, 7, 27, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    @Test
    fun `the delay runs to this evening when the hour is still ahead`() {
        val delay = millisUntilNextHour(millisAt(hour = 9), hour = 21, zone = zone)

        assertEquals(TimeUnit.HOURS.toMillis(12), delay)
    }

    @Test
    fun `after the hour has passed it waits for tomorrow`() {
        val delay = millisUntilNextHour(millisAt(hour = 22), hour = 21, zone = zone)

        assertEquals(TimeUnit.HOURS.toMillis(23), delay)
    }

    /** Exactly on the hour counts as passed, so enabling at 21:00 can't fire twice for one day. */
    @Test
    fun `exactly on the hour waits a full day`() {
        val delay = millisUntilNextHour(millisAt(hour = 21), hour = 21, zone = zone)

        assertEquals(TimeUnit.DAYS.toMillis(1), delay)
    }

    @Test
    fun `the delay is measured in the given zone`() {
        // 09:00 UTC is 18:00 in Tokyo, so the 21:00 recap is three hours out there, not twelve.
        val delay = millisUntilNextHour(millisAt(hour = 9), hour = 21, zone = ZoneId.of("Asia/Tokyo"))

        assertEquals(TimeUnit.HOURS.toMillis(3), delay)
    }
}
