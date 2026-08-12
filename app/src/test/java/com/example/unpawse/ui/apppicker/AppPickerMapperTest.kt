package com.example.unpawse.ui.apppicker

import com.example.unpawse.data.apps.InstalledApp
import com.example.unpawse.data.schedule.EVERY_DAY_MASK
import com.example.unpawse.data.schedule.ScheduleWindow
import com.example.unpawse.data.usage.AppCategory
import com.example.unpawse.data.usage.MonitoredApp
import com.example.unpawse.data.usage.UNLIMITED_MINUTES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPickerMapperTest {

    private val installed = listOf(
        InstalledApp("com.instagram.android", "Instagram", category = AppCategory.SOCIAL),
        InstalledApp("com.spotify.music", "Spotify", category = AppCategory.ENTERTAINMENT),
        // The platform declared nothing for this one.
        InstalledApp("com.zhiliaoapp.musically", "TikTok"),
    )

    @Test
    fun `unmonitored apps default to the starting limit and are off`() {
        val items = toAppLimitItems(installed, monitored = emptyList(), searchQuery = "")

        assertEquals(3, items.size)
        assertTrue(items.none { it.monitored })
        assertTrue(items.all { it.dailyLimitMinutes == DEFAULT_LIMIT_MINUTES })
    }

    @Test
    fun `monitored apps carry their stored limit`() {
        val monitored = listOf(MonitoredApp("com.instagram.android", "Instagram", 45, enabled = true))

        val items = toAppLimitItems(installed, monitored, searchQuery = "")
        val instagram = items.single { it.packageName == "com.instagram.android" }

        assertTrue(instagram.monitored)
        assertEquals(45, instagram.dailyLimitMinutes)
    }

    @Test
    fun `a disabled row reads as unmonitored but keeps its limit`() {
        // Switching an app off preserves the row so the budget survives a re-enable.
        val monitored = listOf(MonitoredApp("com.instagram.android", "Instagram", 45, enabled = false))

        val instagram = toAppLimitItems(installed, monitored, searchQuery = "")
            .single { it.packageName == "com.instagram.android" }

        assertFalse(instagram.monitored)
        assertEquals(45, instagram.dailyLimitMinutes)
    }

    @Test
    fun `search filters by label case-insensitively`() {
        val items = toAppLimitItems(installed, monitored = emptyList(), searchQuery = "tik")

        assertEquals(listOf("TikTok"), items.map { it.label })
    }

    @Test
    fun `blank search returns everything and preserves order`() {
        val items = toAppLimitItems(installed, monitored = emptyList(), searchQuery = "   ")

        assertEquals(listOf("Instagram", "Spotify", "TikTok"), items.map { it.label })
    }

    @Test
    fun `search matching nothing yields an empty list`() {
        assertTrue(toAppLimitItems(installed, monitored = emptyList(), searchQuery = "zzz").isEmpty())
    }

    // --- Weekend override -----------------------------------------------------------------------

    private fun itemFor(app: MonitoredApp) =
        toAppLimitItems(installed, listOf(app), searchQuery = "").single { it.packageName == app.packageName }

    @Test
    fun `no override reads as same as weekdays`() {
        val item = itemFor(MonitoredApp("com.instagram.android", "Instagram", 45, enabled = true))

        assertEquals(WeekendMode.SAME_AS_WEEKDAYS, item.weekendMode)
        // The stepper would open at the everyday budget rather than at the band's floor.
        assertEquals(45, item.weekendStepperMinutes)
    }

    @Test
    fun `a positive override reads as a custom weekend budget`() {
        val item = itemFor(
            MonitoredApp("com.instagram.android", "Instagram", 45, enabled = true, weekendLimitMinutes = 120),
        )

        assertEquals(WeekendMode.CUSTOM, item.weekendMode)
        assertEquals(120, item.weekendStepperMinutes)
    }

    @Test
    fun `the unlimited sentinel reads as no weekend limit`() {
        val item = itemFor(
            MonitoredApp("com.instagram.android", "Instagram", 45, enabled = true, weekendLimitMinutes = UNLIMITED_MINUTES),
        )

        assertEquals(WeekendMode.UNLIMITED, item.weekendMode)
        // Switching back to Custom must not seed the stepper with the negative sentinel.
        assertEquals(45, item.weekendStepperMinutes)
    }

    // --- Schedule summary -----------------------------------------------------------------------

    private fun window(
        id: Long,
        label: String,
        packageName: String? = null,
        enabled: Boolean = true,
    ) = ScheduleWindow(id, label, packageName, 22 * 60, 7 * 60, EVERY_DAY_MASK, enabled)

    @Test
    fun `an app with no windows says so`() {
        assertEquals(NO_SCHEDULES_SUMMARY, scheduleSummaryFor("com.ig", emptyList()))
    }

    @Test
    fun `global and per-app windows both count toward an app's summary`() {
        val windows = listOf(window(1, "Bedtime"), window(2, "School", packageName = "com.ig"))

        assertEquals("Bedtime, School", scheduleSummaryFor("com.ig", windows))
        assertEquals("Bedtime", scheduleSummaryFor("com.tiktok", windows))
    }

    @Test
    fun `a paused window is not advertised as blocking`() {
        val windows = listOf(window(1, "Bedtime", enabled = false))

        assertEquals(NO_SCHEDULES_SUMMARY, scheduleSummaryFor("com.ig", windows))
    }

    @Test
    fun `more than two windows collapse into a count`() {
        val windows = listOf(window(1, "Bedtime"), window(2, "School"), window(3, "Dinner"))

        assertEquals("Bedtime, School, 1 more", scheduleSummaryFor("com.ig", windows))
    }

    @Test
    fun `the summary lands on the item`() {
        val monitored = listOf(MonitoredApp("com.instagram.android", "Instagram", 45, enabled = true))
        val windows = listOf(window(1, "Bedtime"))

        val item = toAppLimitItems(installed, monitored, searchQuery = "", scheduleWindows = windows)
            .single { it.packageName == "com.instagram.android" }

        assertEquals("Bedtime", item.scheduleSummary)
    }

    // --- Category ---------------------------------------------------------------------------------

    @Test
    fun `an unmonitored app shows the platform's guess`() {
        val items = toAppLimitItems(installed, monitored = emptyList(), searchQuery = "")

        assertEquals(AppCategory.SOCIAL, items.single { it.packageName == "com.instagram.android" }.category)
        assertEquals(AppCategory.ENTERTAINMENT, items.single { it.packageName == "com.spotify.music" }.category)
    }

    @Test
    fun `an app the platform never classified shows Other`() {
        val items = toAppLimitItems(installed, monitored = emptyList(), searchQuery = "")

        assertEquals(AppCategory.OTHER, items.single { it.packageName == "com.zhiliaoapp.musically" }.category)
    }

    @Test
    fun `a stored category wins over the platform's guess`() {
        val monitored = listOf(
            MonitoredApp(
                "com.instagram.android", "Instagram", 45, enabled = true,
                category = AppCategory.PRODUCTIVITY,
            ),
        )

        val item = toAppLimitItems(installed, monitored, searchQuery = "")
            .single { it.packageName == "com.instagram.android" }

        assertEquals(AppCategory.PRODUCTIVITY, item.category)
    }
}
