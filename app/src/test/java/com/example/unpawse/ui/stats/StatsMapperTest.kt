package com.example.unpawse.ui.stats

import com.example.unpawse.data.capture.Capture
import com.example.unpawse.data.unlocks.DailyUnlocks
import com.example.unpawse.data.usage.AppCategory
import com.example.unpawse.data.usage.DailyUsage
import com.example.unpawse.data.usage.MonitoredApp
import com.example.unpawse.data.usage.UNLIMITED_MINUTES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class StatsMapperTest {

    private val zone = ZoneId.of("UTC")

    // A Thursday, so the Mon-Sun week has days both before and after it.
    private val today = LocalDate.of(2026, 7, 16)

    private fun app(
        pkg: String,
        label: String,
        limitMinutes: Int,
        enabled: Boolean = true,
        category: AppCategory = AppCategory.OTHER,
        weekendLimitMinutes: Int? = null,
    ) = MonitoredApp(pkg, label, limitMinutes, enabled, weekendLimitMinutes, category)

    private fun usage(
        pkg: String,
        daysAgo: Long,
        usedMinutes: Int,
        earnedMinutes: Int = 0,
        blockedCount: Int = 0,
        from: LocalDate = today,
    ) = DailyUsage(
        pkg,
        from.minusDays(daysAgo).toString(),
        usedMinutes * 60L,
        earnedMinutes * 60L,
        blockedCount,
    )

    private fun map(
        apps: List<MonitoredApp> = emptyList(),
        recentUsage: List<DailyUsage> = emptyList(),
        captures: List<Capture> = emptyList(),
        unlocks: List<DailyUnlocks> = emptyList(),
        allUsage: List<DailyUsage> = recentUsage,
        on: LocalDate = today,
    ) = toStatsUiState(
        monitoredApps = apps,
        recentUsage = recentUsage,
        captures = captures,
        unlocks = unlocks,
        allUsage = allUsage,
        today = on,
        zone = zone,
    )

    private fun unlocks(daysAgo: Long, count: Int) =
        DailyUnlocks(today.minusDays(daysAgo).toString(), count)

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

    /**
     * Any usage at all is greater than zero, so `deltaIsPositive` is true on a first day — which put
     * a red "went up" arrow beside "No data for yesterday". There is no direction to report without
     * a baseline, so the screen draws no arrow.
     */
    @Test
    fun `a first day has no baseline to point an arrow at`() {
        val state = map(recentUsage = listOf(usage("a", 0, 30)))

        assertFalse(state.deltaHasBaseline)
    }

    @Test
    fun `yesterday's usage is a baseline`() {
        val state = map(recentUsage = listOf(usage("a", 0, 30), usage("a", 1, 60)))

        assertTrue(state.deltaHasBaseline)
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

    // --- Category breakdown ---------------------------------------------------------------------

    @Test
    fun `breakdown groups todays usage by category with real durations`() {
        val state = map(
            apps = listOf(
                app("a", "Alpha", 60, category = AppCategory.SOCIAL),
                app("b", "Bravo", 60, category = AppCategory.PRODUCTIVITY),
            ),
            recentUsage = listOf(usage("a", 0, 10), usage("b", 0, 45)),
        )

        assertEquals(listOf("Social", "Productivity"), state.breakdown.map { it.label })
        assertEquals(listOf("10m", "45m"), state.breakdown.map { it.duration })
    }

    @Test
    fun `apps in the same category merge into one slice`() {
        val state = map(
            apps = listOf(
                app("a", "Alpha", 60, category = AppCategory.SOCIAL),
                app("b", "Bravo", 60, category = AppCategory.SOCIAL),
            ),
            recentUsage = listOf(usage("a", 0, 10), usage("b", 0, 45)),
        )

        assertEquals(listOf("Social"), state.breakdown.map { it.label })
        assertEquals(listOf(55 * 60L), state.breakdown.map { it.seconds })
    }

    /**
     * Colour is semantic once the donut is grouped by category, so a bucket must keep its slot
     * whatever its size — otherwise Social changes colour on a day it happens to be the smallest.
     */
    @Test
    fun `buckets come back in declaration order, not by size`() {
        val state = map(
            apps = listOf(
                app("a", "Alpha", 60, category = AppCategory.SOCIAL),
                app("b", "Bravo", 60, category = AppCategory.ENTERTAINMENT),
            ),
            // Entertainment is far larger, but Social is declared first.
            recentUsage = listOf(usage("a", 0, 5), usage("b", 0, 200)),
        )

        assertEquals(listOf("Social", "Entertainment"), state.breakdown.map { it.label })
        assertEquals(listOf(UsageColor.SOCIAL, UsageColor.ENTERTAINMENT), state.breakdown.map { it.color })
    }

    @Test
    fun `an unclassified app counts toward Other`() {
        val state = map(
            apps = listOf(app("a", "Alpha", 60)),
            recentUsage = listOf(usage("a", 0, 10)),
        )

        assertEquals(listOf("Other"), state.breakdown.map { it.label })
        assertEquals(listOf(UsageColor.OTHER), state.breakdown.map { it.color })
    }

    /**
     * The donut used to be `topAppsBreakdown`: the top 3 apps by usage, sized by a 3-entry palette,
     * with the rest silently dropped and no "+N others" bucket — so a device with 4+ monitored apps
     * understated its own screen time. Grouping by category fixed that, and this pins it: whatever
     * the app count, every enabled app's seconds must still be in the donut somewhere.
     */
    @Test
    fun `every enabled app's time reaches the donut however many there are`() {
        // Two apps per bucket, eight in total — well past the three the old palette could hold.
        val apps = AppCategory.entries.flatMap { category ->
            listOf(
                app("${category.name}-1", "First", 60, category = category),
                app("${category.name}-2", "Second", 60, category = category),
            )
        }
        val usageMinutes = 5
        val recentUsage = apps.map { usage(it.packageName, 0, usageMinutes) }

        val state = map(apps = apps, recentUsage = recentUsage)

        assertEquals(
            "no app's time may be dropped",
            apps.size * usageMinutes * 60L,
            state.breakdown.sumOf { it.seconds },
        )
        assertEquals(
            listOf("Social", "Productivity", "Entertainment", "Other"),
            state.breakdown.map { it.label },
        )
    }

    /**
     * The donut describes the apps the user actually asked unPawse to watch. It matters that this is
     * deliberate: the centre figure is summed from these same slices, so both sides agree on scope.
     */
    @Test
    fun `a disabled app is left out of the donut`() {
        val state = map(
            apps = listOf(
                app("a", "Alpha", 60, category = AppCategory.SOCIAL),
                app("b", "Bravo", 60, enabled = false, category = AppCategory.PRODUCTIVITY),
            ),
            recentUsage = listOf(usage("a", 0, 10), usage("b", 0, 45)),
        )

        assertEquals(listOf("Social"), state.breakdown.map { it.label })
        assertEquals(listOf(600L), state.breakdown.map { it.seconds })
    }

    @Test
    fun `a category with no usage today is left out entirely`() {
        val state = map(
            apps = listOf(
                app("a", "Alpha", 60, category = AppCategory.SOCIAL),
                app("b", "Bravo", 60, category = AppCategory.PRODUCTIVITY),
            ),
            recentUsage = listOf(usage("a", 0, 10)),
        )

        assertEquals(listOf("Social"), state.breakdown.map { it.label })
        assertEquals(listOf(600L), state.breakdown.map { it.seconds })
    }

    // --- Donut proportions ----------------------------------------------------------------------
    // The screen used to size arcs from a `durationWeight()` table returning the mockup's literal
    // 72/45/32 keyed off the palette slot, so the donut ignored the durations printed beside it.

    @Test
    fun `breakdown carries the raw seconds behind each duration`() {
        val state = map(
            apps = listOf(
                app("a", "Alpha", 60, category = AppCategory.SOCIAL),
                app("b", "Bravo", 60, category = AppCategory.PRODUCTIVITY),
            ),
            recentUsage = listOf(usage("a", 0, 10), usage("b", 0, 45)),
        )

        assertEquals(listOf(600L, 2700L), state.breakdown.map { it.seconds })
    }

    @Test
    fun `donut proportions follow real durations`() {
        // 3:1 usage must come back as a 3:1 ratio of arc values, whatever the palette says.
        val state = map(
            apps = listOf(
                app("a", "Alpha", 60, category = AppCategory.SOCIAL),
                app("b", "Bravo", 60, category = AppCategory.PRODUCTIVITY),
            ),
            recentUsage = listOf(usage("a", 0, 90), usage("b", 0, 30)),
        )

        val (larger, smaller) = state.breakdown.map { it.seconds }
        assertEquals(3f, larger.toFloat() / smaller, 0.001f)
    }

    // --- Donut centre --------------------------------------------------------------------------
    // The centre used to show budget-left, a measurement the arcs around it know nothing about.

    @Test
    fun `the donut centre is the total of its own slices`() {
        val state = map(
            apps = listOf(
                app("a", "Alpha", 60, category = AppCategory.SOCIAL),
                app("b", "Bravo", 60, category = AppCategory.PRODUCTIVITY),
            ),
            recentUsage = listOf(usage("a", 0, 70), usage("b", 0, 35)),
        )

        assertEquals("1h 45m", state.breakdownTotal)
        assertEquals(105 * 60L, state.breakdown.sumOf { it.seconds })
    }

    /**
     * `dailyTotal` counts every usage row, including monitored-but-disabled apps the donut leaves
     * out, so the centre has to be summed from the slices rather than reusing it.
     */
    @Test
    fun `the centre ignores usage the donut does not draw`() {
        val state = map(
            apps = listOf(
                app("a", "Alpha", 60, category = AppCategory.SOCIAL),
                app("off", "Off", 60, enabled = false, category = AppCategory.PRODUCTIVITY),
            ),
            recentUsage = listOf(usage("a", 0, 30), usage("off", 0, 90)),
        )

        assertEquals("30m", state.breakdownTotal)
        assertEquals("the daily total still counts everything", "2h", state.dailyTotal)
    }

    /** A day made entirely of uncapped apps has no headroom to report, so it blanks. */
    @Test
    fun `budget left blanks when nothing has a cap`() {
        val state = map(
            apps = listOf(app("free", "Free", UNLIMITED_MINUTES)),
            recentUsage = listOf(usage("free", 0, 120)),
        )

        assertEquals("—", state.budgetLeftLabel)
    }

    @Test
    fun `budget left percent reflects real usage`() {
        val state = map(
            apps = listOf(app("a", "Alpha", 60)),
            recentUsage = listOf(usage("a", 0, 15)),
        )

        assertEquals("75%", state.budgetLeftLabel)
    }

    /** The Saturday budget must be the one the blocker would enforce, not the weekday figure. */
    @Test
    fun `budget left follows the weekend override`() {
        val saturday = LocalDate.of(2026, 7, 18)
        val apps = listOf(app("a", "Alpha", 30, weekendLimitMinutes = 120))

        val onSaturday = map(apps = apps, recentUsage = listOf(usage("a", 0, 30, from = saturday)), on = saturday)
        assertEquals("75%", onSaturday.budgetLeftLabel)

        // The same 30 minutes against the weekday budget is the whole allowance.
        assertEquals("0%", map(apps = apps, recentUsage = listOf(usage("a", 0, 30))).budgetLeftLabel)
    }

    /**
     * An unlimited app used to contribute a negative budget, so its own usage was charged against
     * everyone else. One uncapped app made the whole day report "0% left".
     */
    @Test
    fun `an uncapped app does not spend the capped apps budget`() {
        val state = map(
            apps = listOf(app("a", "Alpha", 60), app("free", "Free", UNLIMITED_MINUTES)),
            recentUsage = listOf(usage("a", 0, 15), usage("free", 0, 300)),
        )

        assertEquals("75%", state.budgetLeftLabel)
    }

    @Test
    fun `nothing monitored does not divide by zero`() {
        val state = map()

        assertEquals("—", state.budgetLeftLabel)
        assertEquals("0m", state.dailyTotal)
        assertTrue(state.breakdown.isEmpty())
        assertEquals("0m", state.breakdownTotal)
    }

    @Test
    fun `trend compares this week against last week`() {
        // 2h this week, 1h last week -> +1.0h.
        val state = map(recentUsage = listOf(usage("a", 0, 120), usage("a", 8, 60)))

        assertEquals("+1.0h", state.trendLabel)
    }

    @Test
    fun `no history yields real zeros, never sample's figures`() {
        // Every metric is backed now, so the guard changes shape: what must never come back is
        // `sample()`'s invented data leaking through the `.copy(...)` base. A fresh install reports
        // honest emptiness — no blocks, no unlocks observed, nothing earned — and, the actual
        // regression this pins, a daily total the mapper computed rather than the mockup's "3h 24m".
        val state = map(recentUsage = listOf(usage("a", 0, 30)))

        assertEquals(0, state.preventedCount)
        assertEquals("—", state.unlocks)
        assertTrue("no badge may claim to be earned", state.achievements.none { it.unlocked })
        assertNotEquals(StatsUiState.sample().dailyTotal, state.dailyTotal)
        assertNotEquals(StatsUiState.sample().preventedCount, state.preventedCount)
    }

    /**
     * The mapper builds [StatsUiState] field by field rather than from `sample().copy(...)`, so its
     * one remaining constant has to come from the mapper's own value. If someone reintroduces the
     * `.copy(...)` base this still passes — which is why the test above also pins a *computed*
     * field against `sample()`.
     */
    @Test
    fun `the fixed axis comes from the mapper, not the mockup`() {
        val state = map()

        assertEquals(listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"), state.weekdayLabels)
    }

    @Test
    fun `the achievements rail is populated even with nothing earned`() {
        // An empty rail under a heading reads as content that failed to load; locked cards are
        // content, telling the user what there is to earn.
        val state = map()

        assertEquals(ACHIEVEMENT_CATALOGUE.size, state.achievements.size)
    }

    @Test
    fun `a badge earned from real history is marked unlocked`() {
        val capture = Capture("id", "/tmp/x.jpg", capturedAt = 0L, confidence = 0.9f, isBonus = false)

        val state = map(captures = listOf(capture))

        assertTrue(state.achievements.single { it.title == "First Cat" }.unlocked)
    }

    // --- Unlocks --------------------------------------------------------------------------------

    @Test
    fun `never having seen an unlock reads as no data, not zero`() {
        // The monitor service may never have run, so there is nothing to report — as opposed to a
        // day on which the user genuinely didn't unlock.
        val state = map(recentUsage = listOf(usage("a", 0, 30)))

        assertEquals("—", state.unlocks)
    }

    @Test
    fun `todays unlocks are reported as a plain count`() {
        val state = map(unlocks = listOf(unlocks(0, 17)))

        assertEquals("17", state.unlocks)
    }

    @Test
    fun `a day with history but none today is a real zero`() {
        val state = map(unlocks = listOf(unlocks(1, 24)))

        assertEquals("0", state.unlocks)
    }

    @Test
    fun `yesterdays unlocks never leak into todays count`() {
        val state = map(unlocks = listOf(unlocks(0, 3), unlocks(1, 40), unlocks(2, 40)))

        assertEquals("3", state.unlocks)
    }

    // --- Prevented ------------------------------------------------------------------------------

    @Test
    fun `prevented sums blocks across the week and every app`() {
        val state = map(
            recentUsage = listOf(
                usage("a", 0, 30, blockedCount = 2),
                usage("b", 0, 10, blockedCount = 1),
                // Tuesday of the same week (today is Thursday).
                usage("a", 2, 20, blockedCount = 3),
            ),
        )

        assertEquals(6, state.preventedCount)
    }

    @Test
    fun `no blocks reads as zero rather than sample's 42`() {
        val state = map(recentUsage = listOf(usage("a", 0, 30)))

        assertEquals(0, state.preventedCount)
    }

    /**
     * Same rule the trend follows: the card says "THIS WEEK", so it must mean the Mon–Sun week the
     * chart draws, not a rolling seven days.
     */
    @Test
    fun `prevented counts the same calendar week the chart draws`() {
        // today is Thursday 2026-07-16; Monday is the 13th, so 4 days ago (the 12th) is last week.
        val state = map(
            recentUsage = listOf(
                usage("a", 4, 60, blockedCount = 9),
                usage("a", 0, 60, blockedCount = 1),
            ),
        )

        assertEquals("last week's 9 blocks must not count", 1, state.preventedCount)
    }

    @Test
    fun `capture count is the lifetime total`() {
        val captures = List(3) {
            Capture("id$it", "/tmp/x.jpg", capturedAt = 0L, confidence = 0.9f, isBonus = false)
        }

        assertEquals("3 Photos", map(captures = captures).capturedPhotos)
    }

    // --- Trend direction ------------------------------------------------------------------------
    // The arrow used to be hardcoded to TrendingDown, so a week where usage rose rendered "+0.6h"
    // beside a downward arrow.

    @Test
    fun `a heavier week is flagged as trending up`() {
        val state = map(recentUsage = listOf(usage("a", 0, 120), usage("a", 8, 60)))

        assertEquals("+1.0h", state.trendLabel)
        assertTrue(state.trendIsUp)
    }

    @Test
    fun `a lighter week is flagged as trending down`() {
        val state = map(recentUsage = listOf(usage("a", 0, 60), usage("a", 8, 120)))

        assertEquals("-1.0h", state.trendLabel)
        assertFalse(state.trendIsUp)
    }

    @Test
    fun `two identical weeks are neither up nor signed`() {
        val state = map(recentUsage = listOf(usage("a", 0, 60), usage("a", 8, 60)))

        assertEquals("0.0h", state.trendLabel)
        assertFalse(state.trendIsUp)
    }

    @Test
    fun `an unchanged zero week reads as no change rather than a decrease`() {
        assertEquals("0.0h", trendLabel(0))
    }

    /**
     * The trend used to sum rolling 7-day windows while the chart drew Mon–Sun, so on a Monday it
     * counted days the chart didn't show — "usage up" beside a flat week.
     */
    @Test
    fun `the trend uses the same calendar week the chart draws`() {
        // today is Thursday 2026-07-16; Monday of this week is the 13th, so 7 days ago (Thursday
        // the 9th) is last week and must not count toward this week.
        val state = map(recentUsage = listOf(usage("a", 7, 60)))

        assertEquals("this week saw no usage, last week saw an hour", "-1.0h", state.trendLabel)
        assertFalse(state.trendIsUp)
    }

    // --- Trend baseline and window --------------------------------------------------------------
    // Last week used to be summed whole against a Monday-to-today this week: on a Wednesday, 3 days
    // measured against 7, so the figure read hugely negative every Monday and drifted up all week.

    /**
     * A fresh install has no last week, so `lastWeekSeconds` was 0 and any usage at all rendered a
     * signed figure with an upward arrow — a week-over-week change against a week that never was.
     */
    @Test
    fun `a first install has no week to compare against`() {
        val state = map(recentUsage = listOf(usage("a", 0, 30)))

        assertFalse(state.trendHasBaseline)
        assertEquals("—", state.trendLabel)
        assertEquals("NO DATA FOR LAST WEEK", state.trendCaption)
    }

    @Test
    fun `last week's later days are outside the comparison`() {
        // Sunday the 12th is last week, but past today's weekday — this week has no Sunday yet, so
        // counting it would compare 4 days against 7.
        val state = map(recentUsage = listOf(usage("a", 4, 60)))

        assertFalse("nothing comparable was measured last Mon-Thu", state.trendHasBaseline)
        assertEquals("—", state.trendLabel)
    }

    @Test
    fun `the same weekday last week is the baseline`() {
        val state = map(recentUsage = listOf(usage("a", 0, 60), usage("a", 7, 60)))

        assertTrue(state.trendHasBaseline)
        assertEquals("an equal Thursday is no change", "0.0h", state.trendLabel)
        assertEquals("VS LAST WEEK, SAME DAYS", state.trendCaption)
    }

    // --- Trend sparkline ------------------------------------------------------------------------
    // The bars used to be a rolling five days while the headline above them compared calendar
    // weeks: two windows in one card, with nothing to tell them apart.

    @Test
    fun `the sparkline covers the same week as the headline`() {
        // today is Thursday; Tuesday saw half of today's usage.
        val state = map(recentUsage = listOf(usage("a", 0, 60), usage("a", 2, 30)))

        assertEquals(7, state.trendBars.size)
        assertEquals(state.weekdayLabels.size, state.trendBars.size)
        assertEquals(1f, state.trendBars[3]!!, 0.001f)
        assertEquals(0.5f, state.trendBars[1]!!, 0.001f)
        assertEquals("Monday saw nothing, which is a real zero", 0f, state.trendBars[0]!!, 0.001f)
    }

    @Test
    fun `days still to come have no bar`() {
        val state = map(recentUsage = listOf(usage("a", 0, 60)))

        assertTrue("Fri-Sun have not happened", state.trendBars.takeLast(3).all { it == null })
        assertTrue("Mon-Thu have", state.trendBars.take(4).none { it == null })
    }

    @Test
    fun `a week with no usage still has a bar per elapsed day`() {
        val state = map()

        assertEquals(listOf(0f, 0f, 0f, 0f), state.trendBars.take(4))
    }

    // --- Streak label ---------------------------------------------------------------------------

    @Test
    fun `a one-day streak is singular`() {
        assertEquals("1 Day", dayCountLabel(1))
    }

    @Test
    fun `other day counts are plural`() {
        assertEquals("0 Days", dayCountLabel(0))
        assertEquals("2 Days", dayCountLabel(2))
    }
}
