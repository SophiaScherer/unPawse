package com.example.unpawse.ui.home

import com.example.unpawse.data.capture.Capture
import com.example.unpawse.data.usage.DailyUsage
import com.example.unpawse.data.usage.MonitoredApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class HomeMapperTest {

    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 7, 16)
    private val morning = LocalTime.of(9, 0)

    private fun app(pkg: String, label: String, limitMinutes: Int, enabled: Boolean = true) =
        MonitoredApp(pkg, label, limitMinutes, enabled)

    private fun usage(pkg: String, usedMinutes: Int, earnedMinutes: Int = 0) =
        DailyUsage(pkg, today.toString(), usedMinutes * 60L, earnedMinutes * 60L)

    private fun capture(daysAgo: Long, hour: Int = 10, confidence: Float = 0.9f) = Capture(
        id = "id-$daysAgo-$hour",
        filePath = "/tmp/x.jpg",
        capturedAt = ZonedDateTime.of(today.minusDays(daysAgo).atTime(hour, 0), zone)
            .toInstant().toEpochMilli(),
        confidence = confidence,
        isBonus = false,
    )

    private fun map(
        apps: List<MonitoredApp>,
        todayUsage: List<DailyUsage> = emptyList(),
        captures: List<Capture> = emptyList(),
        userName: String = "",
        time: LocalTime = morning,
    ) = toHomeUiState(apps, todayUsage, captures, userName, today, zone, time)

    @Test
    fun `totals sum across monitored apps`() {
        val state = map(
            apps = listOf(app("a", "Alpha", 60), app("b", "Bravo", 60)),
            todayUsage = listOf(usage("a", 30), usage("b", 15)),
        )

        assertEquals("45m", state.screenTimeUsedLabel)
        // 120m budget, 45m used → 75m left, 37.5% burned.
        assertEquals("1h 15m", state.remainingLabel)
        assertEquals(0.375f, state.progressFraction, 0.001f)
    }

    @Test
    fun `earned minutes extend the remaining budget`() {
        val state = map(
            apps = listOf(app("a", "Alpha", 30)),
            todayUsage = listOf(usage("a", 30, earnedMinutes = 15)),
        )

        assertEquals("15m", state.remainingLabel)
    }

    @Test
    fun `disabled apps count for nothing`() {
        val state = map(
            apps = listOf(app("a", "Alpha", 60), app("b", "Bravo", 60, enabled = false)),
            todayUsage = listOf(usage("a", 30), usage("b", 60)),
        )

        assertEquals("30m", state.screenTimeUsedLabel)
        assertEquals(1, state.pausedAppsCount)
    }

    @Test
    fun `nothing monitored does not divide by zero`() {
        val state = map(apps = emptyList())

        assertEquals(0f, state.progressFraction, 0f)
        assertEquals("0m", state.screenTimeUsedLabel)
        assertEquals(0, state.pausedAppsCount)
    }

    @Test
    fun `progress never exceeds full even when over budget`() {
        val state = map(
            apps = listOf(app("a", "Alpha", 10)),
            todayUsage = listOf(usage("a", 60)),
        )

        assertEquals(1f, state.progressFraction, 0f)
        assertEquals("0m", state.remainingLabel)
    }

    @Test
    fun `cat count is todays captures only`() {
        val state = map(
            apps = emptyList(),
            captures = listOf(capture(daysAgo = 0), capture(daysAgo = 0, hour = 12), capture(daysAgo = 1)),
        )

        assertEquals(2, state.catCount)
    }

    @Test
    fun `an over-budget app shows as blocked activity`() {
        val state = map(
            apps = listOf(app("a", "Alpha", 10)),
            todayUsage = listOf(usage("a", 30)),
        )

        val blocked = state.activities.single { it.kind == ActivityKind.BLOCKED }
        assertEquals("Alpha Blocked", blocked.title)
        assertEquals("Daily limit of 10m reached.", blocked.subtitle)
    }

    @Test
    fun `an in-budget app produces no blocked activity`() {
        val state = map(
            apps = listOf(app("a", "Alpha", 60)),
            todayUsage = listOf(usage("a", 5)),
        )

        assertTrue(state.activities.none { it.kind == ActivityKind.BLOCKED })
    }

    @Test
    fun `a set name drives the greeting name and avatar initial`() {
        val state = map(apps = emptyList(), userName = "sophia")

        assertEquals("sophia", state.userName)
        assertEquals('S', state.avatarInitial)
    }

    @Test
    fun `a blank name falls back to a friendly default`() {
        val state = map(apps = emptyList(), userName = "")

        assertEquals("friend", state.userName)
        assertEquals('F', state.avatarInitial)
    }

    @Test
    fun `banner guides setup when nothing is monitored`() {
        val banner = buildBanner(streakDays = 0, remainingSeconds = 0, budgetSeconds = 0)
        assertEquals("Welcome to unPawse!", banner.title)
    }

    @Test
    fun `banner celebrates a streak of three or more`() {
        val banner = buildBanner(streakDays = 5, remainingSeconds = 3600, budgetSeconds = 7200)
        assertEquals("🔥 5-day streak!", banner.title)
    }

    @Test
    fun `banner nudges when the budget is spent`() {
        val banner = buildBanner(streakDays = 0, remainingSeconds = 0, budgetSeconds = 7200)
        assertEquals("Limit reached", banner.title)
    }

    @Test
    fun `banner warns when little time is left`() {
        val banner = buildBanner(streakDays = 0, remainingSeconds = 5 * 60, budgetSeconds = 7200)
        assertEquals("Almost there", banner.title)
        assertEquals("Only 5m of screen time left today.", banner.body)
    }

    @Test
    fun `banner is upbeat with plenty of budget left`() {
        val banner = buildBanner(streakDays = 1, remainingSeconds = 3600, budgetSeconds = 7200)
        assertEquals("Looking sharp today!", banner.title)
    }

    @Test
    fun `greeting follows the time of day`() {
        assertEquals("Good morning,", greetingFor(LocalTime.of(6, 0)))
        assertEquals("Good afternoon,", greetingFor(LocalTime.of(13, 0)))
        assertEquals("Good evening,", greetingFor(LocalTime.of(20, 0)))
        assertEquals("Good evening,", greetingFor(LocalTime.of(2, 0)))
    }

    @Test
    fun `todays captures appear as verified activity with a real time`() {
        val state = map(apps = emptyList(), captures = listOf(capture(daysAgo = 0, hour = 14, confidence = 0.83f)))

        val verified = state.activities.single { it.kind == ActivityKind.VERIFIED }
        assertEquals("Cat Verified", verified.title)
        assertEquals("83% match. Time earned back.", verified.subtitle)
        assertEquals("2:00 PM", verified.time)
    }
}
