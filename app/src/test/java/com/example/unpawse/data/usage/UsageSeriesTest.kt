package com.example.unpawse.data.usage

import com.example.unpawse.data.apps.InstalledApp
import com.example.unpawse.data.apps.platformCategories
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** The fold both Stats scopes go through, and the category precedence the all-apps donut needs. */
class UsageSeriesTest {

    private val today = LocalDate.of(2026, 7, 16)

    private fun app(
        pkg: String,
        enabled: Boolean = true,
        category: AppCategory = AppCategory.OTHER,
    ) = MonitoredApp(pkg, pkg, 60, enabled, null, category)

    private fun usage(pkg: String, daysAgo: Long, seconds: Long) =
        DailyUsage(pkg, today.minusDays(daysAgo).toString(), seconds, 0L, 0)

    @Test
    fun `tracked days sum every row for that date`() {
        val series = trackedUsageSeries(
            listOf(usage("a", 0, 600), usage("b", 0, 300), usage("a", 1, 60)),
            listOf(app("a"), app("b")),
            today,
        )

        assertEquals(900L, series.secondsByDate.getValue(today.toString()))
        assertEquals(60L, series.secondsByDate.getValue(today.minusDays(1).toString()))
    }

    @Test
    fun `a date with no rows is absent rather than zero`() {
        val series = trackedUsageSeries(listOf(usage("a", 0, 600)), listOf(app("a")), today)

        // The chart tells "no measurement" from "an idle day" by presence, so this must not be 0L.
        assertFalse(series.secondsByDate.containsKey(today.minusDays(1).toString()))
    }

    @Test
    fun `a disabled app still counts toward the day but drops out of the donut`() {
        // The pre-existing asymmetry, pinned deliberately: disabling an app must not rewrite the
        // chart's history, but it does stop the app claiming a slice today.
        val series = trackedUsageSeries(
            listOf(usage("a", 0, 600), usage("off", 0, 300)),
            listOf(app("a"), app("off", enabled = false)),
            today,
        )

        assertEquals(900L, series.secondsByDate.getValue(today.toString()))
        assertTrue(series.todaySecondsByPackage.containsKey("off"))
        assertFalse(series.categories.containsKey("off"))
    }

    @Test
    fun `device days sum every package the platform reported`() {
        val series = deviceUsageSeries(
            mapOf(today.toString() to mapOf("a" to 600L, "b" to 300L)),
            platformCategories = emptyMap(),
            monitoredApps = emptyList(),
            today = today,
        )

        assertEquals(900L, series.secondsByDate.getValue(today.toString()))
        assertEquals(mapOf("a" to 600L, "b" to 300L), series.todaySecondsByPackage)
    }

    @Test
    fun `a day the platform could not report stays absent`() {
        // Beyond the platform's roughly-a-week of daily buckets. Absent, so no mark is drawn and the
        // trend finds no baseline — not a zero claiming a day spent off the phone.
        val series = deviceUsageSeries(
            mapOf(today.toString() to mapOf("a" to 600L)),
            emptyMap(),
            emptyList(),
            today,
        )

        assertFalse(series.secondsByDate.containsKey(today.minusDays(9).toString()))
    }

    @Test
    fun `every app the platform reported gets a bucket`() {
        val series = deviceUsageSeries(
            mapOf(today.toString() to mapOf("com.unknown" to 600L)),
            // Not launchable, so it never appeared in the installed list either.
            platformCategories = emptyMap(),
            monitoredApps = emptyList(),
            today = today,
        )

        // Nothing may vanish from the donut: an app we cannot classify is what OTHER means.
        assertEquals(AppCategory.OTHER, series.categories.getValue("com.unknown"))
    }

    @Test
    fun `the day the platform's history starts is partial, so it is not measured`() {
        val edge = today.minusDays(9)
        val series = deviceUsageSeries(
            mapOf(edge.toString() to mapOf("a" to 3600L), today.minusDays(8).toString() to mapOf("a" to 32_400L)),
            platformCategories = emptyMap(),
            monitoredApps = emptyList(),
            today = today,
            readSince = today.minusDays(13),
        )

        assertEquals(today.minusDays(8), series.measuredSince)
    }

    @Test
    fun `history reaching the start of the read is measured from there`() {
        val start = today.minusDays(13)
        val series = deviceUsageSeries(
            mapOf(start.toString() to mapOf("a" to 3600L), today.toString() to mapOf("a" to 60L)),
            platformCategories = emptyMap(),
            monitoredApps = emptyList(),
            today = today,
            readSince = start,
        )

        assertEquals(start, series.measuredSince)
    }

    @Test
    fun `a first day of usage today stays measured`() {
        val series = deviceUsageSeries(
            mapOf(today.toString() to mapOf("a" to 60L)),
            platformCategories = emptyMap(),
            monitoredApps = emptyList(),
            today = today,
            readSince = today.minusDays(13),
        )

        assertEquals(today, series.measuredSince)
    }

    @Test
    fun `a stored category beats the platform's guess`() {
        val installed = listOf(InstalledApp("com.ig", "Instagram", AppCategory.SOCIAL))

        val series = deviceUsageSeries(
            mapOf(today.toString() to mapOf("com.ig" to 600L)),
            installed.platformCategories(),
            listOf(app("com.ig", category = AppCategory.PRODUCTIVITY)),
            today,
        )

        assertEquals(AppCategory.PRODUCTIVITY, series.categories.getValue("com.ig"))
    }

    @Test
    fun `the platform's guess is used where the user never chose`() {
        val installed = listOf(InstalledApp("com.ig", "Instagram", AppCategory.SOCIAL))

        val series = deviceUsageSeries(
            mapOf(today.toString() to mapOf("com.ig" to 600L)),
            installed.platformCategories(),
            monitoredApps = emptyList(),
            today = today,
        )

        assertEquals(AppCategory.SOCIAL, series.categories.getValue("com.ig"))
    }

    @Test
    fun `a disabled app keeps the category the user gave it`() {
        // Switching an app off stops it being limited; it does not retract what the user said it was.
        val resolved = resolveCategories(
            mapOf("com.ig" to AppCategory.SOCIAL),
            listOf(app("com.ig", enabled = false, category = AppCategory.PRODUCTIVITY)),
        )

        assertEquals(AppCategory.PRODUCTIVITY, resolved.getValue("com.ig"))
    }

    @Test
    fun `an unclassified install contributes no entry rather than a null one`() {
        val resolved = resolveCategories(mapOf("com.mystery" to null), emptyList())

        assertFalse(resolved.containsKey("com.mystery"))
    }

    @Test
    fun `tracking is measured from the first row, and never later than today`() {
        assertEquals(today.minusDays(3), firstMeasuredDay(listOf(usage("a", 0, 1), usage("a", 3, 1)), today))
        assertEquals("nothing tracked yet still measures today", today, firstMeasuredDay(emptyList(), today))
    }

    @Test
    fun `the platform is measured from the first day it still holds`() {
        // An empty day it holds is a measured zero; days it no longer holds are simply absent.
        val series = deviceUsageSeries(
            secondsByDateAndPackage = mapOf(
                today.minusDays(9).toString() to emptyMap(),
                today.minusDays(8).toString() to mapOf("a" to 60L),
                today.toString() to mapOf("a" to 60L),
            ),
            platformCategories = emptyMap(),
            monitoredApps = emptyList(),
            today = today,
        )

        assertEquals(today.minusDays(9), series.measuredSince)
    }

    @Test
    fun `the live window overrides a stale history read`() {
        // Read before midnight, the history holds yesterday's partial total; the window has the final one.
        val stale = listOf(usage("a", 20, 600), usage("a", 1, 60))
        val live = listOf(usage("a", 1, 900), usage("a", 0, 30))

        val merged = mergeUsageHistory(stale, live).associate { it.date to it.usedSeconds }

        assertEquals(600L, merged.getValue(today.minusDays(20).toString()))
        assertEquals(900L, merged.getValue(today.minusDays(1).toString()))
        assertEquals(30L, merged.getValue(today.toString()))
        assertEquals(3, merged.size)
    }
}
