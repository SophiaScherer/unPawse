package com.example.unpawse.ui.stats

import com.example.unpawse.data.capture.Capture
import com.example.unpawse.data.usage.DailyUsage
import com.example.unpawse.data.usage.MonitoredApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class StatsMapperTest {

    private val zone = ZoneId.of("UTC")

    // A Thursday, so the Mon-Sun week has days both before and after it.
    private val today = LocalDate.of(2026, 7, 16)

    private fun app(pkg: String, label: String, limitMinutes: Int, enabled: Boolean = true) =
        MonitoredApp(pkg, label, limitMinutes, enabled)

    private fun usage(pkg: String, daysAgo: Long, usedMinutes: Int, earnedMinutes: Int = 0) =
        DailyUsage(pkg, today.minusDays(daysAgo).toString(), usedMinutes * 60L, earnedMinutes * 60L)

    private fun map(
        apps: List<MonitoredApp> = emptyList(),
        recentUsage: List<DailyUsage> = emptyList(),
        captures: List<Capture> = emptyList(),
    ) = toStatsUiState(apps, recentUsage, captures, today, zone)

    @Test
    fun `daily total sums todays usage across apps`() {
        val state = map(recentUsage = listOf(usage("a", 0, 60), usage("b", 0, 24)))

        assertEquals("1h 24m", state.dailyTotal)
    }

    @Test
    fun `delta compares against yesterday and flags an increase`() {
        val state = map(recentUsage = listOf(usage("a", 0, 120), usage("a", 1, 60)))

        assertEquals("100% from yesterday", state.deltaText)
        assertTrue(state.deltaIsPositive)
    }

    @Test
    fun `a decrease is not flagged positive`() {
        val state = map(recentUsage = listOf(usage("a", 0, 30), usage("a", 1, 60)))

        assertEquals("50% from yesterday", state.deltaText)
        assertFalse(state.deltaIsPositive)
    }

    @Test
    fun `no yesterday data says so rather than dividing by zero`() {
        val state = map(recentUsage = listOf(usage("a", 0, 30)))

        assertEquals("No data for yesterday", state.deltaText)
    }

    @Test
    fun `weekly points are hours per weekday with gaps as zero`() {
        // today is Thursday -> index 3 in a Mon-first week.
        val state = map(recentUsage = listOf(usage("a", 0, 120)))

        assertEquals(7, state.weeklyPoints.size)
        assertEquals(3, state.highlightDayIndex)
        assertEquals(2f, state.weeklyPoints[3], 0.001f)
        assertEquals(0f, state.weeklyPoints[0], 0.001f)
    }

    @Test
    fun `breakdown lists todays busiest apps with real durations`() {
        val state = map(
            apps = listOf(app("a", "Alpha", 60), app("b", "Bravo", 60)),
            recentUsage = listOf(usage("a", 0, 10), usage("b", 0, 45)),
        )

        assertEquals(listOf("Bravo", "Alpha"), state.breakdown.map { it.label })
        assertEquals(listOf("45m", "10m"), state.breakdown.map { it.duration })
    }

    @Test
    fun `unused apps stay out of the breakdown`() {
        val state = map(
            apps = listOf(app("a", "Alpha", 60), app("b", "Bravo", 60)),
            recentUsage = listOf(usage("a", 0, 10)),
        )

        assertEquals(listOf("Alpha"), state.breakdown.map { it.label })
    }

    @Test
    fun `budget left percent reflects real usage`() {
        val state = map(
            apps = listOf(app("a", "Alpha", 60)),
            recentUsage = listOf(usage("a", 0, 15)),
        )

        assertEquals(75, state.productivePercent)
    }

    @Test
    fun `nothing monitored does not divide by zero`() {
        val state = map()

        assertEquals(0, state.productivePercent)
        assertEquals("0m", state.dailyTotal)
        assertTrue(state.breakdown.isEmpty())
    }

    @Test
    fun `trend compares this week against last week`() {
        // 2h this week, 1h last week -> +1.0h.
        val state = map(recentUsage = listOf(usage("a", 0, 120), usage("a", 8, 60)))

        assertEquals("+1.0h", state.trendLabel)
    }

    @Test
    fun `metrics with no backing data are blanked, not faked`() {
        // sample() carries invented figures (42 prevented, "24/day" unlocks, two achievements).
        // Showing those beside real numbers would read as fact, so they must be blanked until the
        // features behind them exist.
        val state = map(recentUsage = listOf(usage("a", 0, 30)))

        assertEquals(0, state.preventedCount)
        assertEquals("—", state.unlocks)
        assertTrue(state.achievements.isEmpty())
    }

    @Test
    fun `capture count is the lifetime total`() {
        val captures = List(3) {
            Capture("id$it", "/tmp/x.jpg", capturedAt = 0L, confidence = 0.9f, isBonus = false)
        }

        assertEquals("3 Photos", map(captures = captures).capturedPhotos)
    }
}
