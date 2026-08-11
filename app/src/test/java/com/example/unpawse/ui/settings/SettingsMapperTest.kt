package com.example.unpawse.ui.settings

import com.example.unpawse.BuildConfig
import com.example.unpawse.data.schedule.ScheduleWindow
import com.example.unpawse.data.usage.MonitoredApp
import com.example.unpawse.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsMapperTest {

    private fun app(label: String, enabled: Boolean = true, limitMinutes: Int = 30) =
        MonitoredApp("com.$label".lowercase(), label, dailyLimitMinutes = limitMinutes, enabled = enabled)

    private fun state(
        userName: String = "",
        sensitivity: Float = 0.65f,
        dailySummaryEnabled: Boolean = false,
        earnedMinutesPerCat: Int = 15,
        warningMinutes: Int = 5,
        reminderMinutes: Int = 0,
        photoCount: Int = 0,
        photoStorageBytes: Long = 0L,
        monitoredApps: List<MonitoredApp> = emptyList(),
        scheduleWindows: List<ScheduleWindow> = emptyList(),
        usageAccessGranted: Boolean = false,
        overlayAccessGranted: Boolean = false,
        notificationsGranted: Boolean = false,
        versionLabel: String = "1.0 (1)",
    ) = toSettingsUiState(
        userName = userName,
        sensitivity = sensitivity,
        dailySummaryEnabled = dailySummaryEnabled,
        earnedMinutesPerCat = earnedMinutesPerCat,
        warningMinutes = warningMinutes,
        reminderMinutes = reminderMinutes,
        photoCount = photoCount,
        photoStorageBytes = photoStorageBytes,
        monitoredApps = monitoredApps,
        scheduleWindows = scheduleWindows,
        usageAccessGranted = usageAccessGranted,
        overlayAccessGranted = overlayAccessGranted,
        notificationsGranted = notificationsGranted,
        versionLabel = versionLabel,
    )

    @Test
    fun `no apps reads as a prompt rather than an empty string`() {
        assertEquals("No apps limited yet", monitoredAppsSummary(emptyList()))
    }

    @Test
    fun `one or two apps are named outright`() {
        assertEquals("Instagram", monitoredAppsSummary(listOf(app("Instagram"))))
        assertEquals("Instagram, TikTok", monitoredAppsSummary(listOf(app("Instagram"), app("TikTok"))))
    }

    @Test
    fun `a third app collapses into a singular other`() {
        val apps = listOf(app("Instagram"), app("TikTok"), app("Reddit"))

        assertEquals("Instagram, TikTok, 1 other", monitoredAppsSummary(apps))
    }

    @Test
    fun `more apps collapse into plural others matching the mockup`() {
        val apps = listOf(app("Instagram"), app("TikTok"), app("Reddit"), app("YouTube"), app("Spotify"))

        assertEquals("Instagram, TikTok, 3 others", monitoredAppsSummary(apps))
    }

    @Test
    fun `disabled apps are excluded from the summary`() {
        val apps = listOf(app("Instagram"), app("TikTok", enabled = false))

        assertEquals("Instagram", monitoredAppsSummary(apps))
    }

    @Test
    fun `persisted values reach the ui state`() {
        val ui = state(
            userName = "Sophia",
            sensitivity = 0.8f,
            dailySummaryEnabled = true,
            earnedMinutesPerCat = 30,
            monitoredApps = listOf(app("Instagram")),
            usageAccessGranted = true,
            overlayAccessGranted = true,
            notificationsGranted = true,
        )

        assertEquals("Sophia", ui.userName)
        assertEquals(0.8f, ui.sensitivity, 0.0001f)
        assertTrue(ui.dailySummaryEnabled)
        assertEquals(30, ui.earnedMinutesPerCat)
        assertEquals("Instagram", ui.appLimitsSummary)
        assertEquals("30m across 1 app", ui.dailyLimitLabel)
        assertTrue(ui.usageAccessGranted)
        assertTrue(ui.overlayAccessGranted)
        assertTrue(ui.notificationsGranted)
    }

    @Test
    fun `no limits reads as a prompt rather than zero minutes`() {
        assertEquals("No limits set yet", dailyLimitSummary(emptyList()))
    }

    @Test
    fun `a single limit is counted in the singular`() {
        assertEquals("45m across 1 app", dailyLimitSummary(listOf(app("Instagram", limitMinutes = 45))))
    }

    @Test
    fun `limits are summed and rendered as hours and minutes`() {
        val apps = listOf(
            app("Instagram", limitMinutes = 45),
            app("TikTok", limitMinutes = 90),
            app("Reddit", limitMinutes = 120),
        )

        assertEquals("4h 15m across 3 apps", dailyLimitSummary(apps))
    }

    @Test
    fun `a whole number of hours drops the minutes`() {
        val apps = listOf(app("Instagram", limitMinutes = 60), app("TikTok", limitMinutes = 60))

        assertEquals("2h across 2 apps", dailyLimitSummary(apps))
    }

    @Test
    fun `disabled apps do not count toward the total`() {
        val apps = listOf(
            app("Instagram", limitMinutes = 45),
            app("TikTok", enabled = false, limitMinutes = 90),
        )

        assertEquals("45m across 1 app", dailyLimitSummary(apps))
    }

    @Test
    fun `the reminder interval reads as a frequency, and off when disabled`() {
        assertEquals("Off", reminderLabel(0))
        assertEquals("Every 30m", reminderLabel(30))
        assertEquals("Every 1h", reminderLabel(60))
    }

    @Test
    fun `the warning threshold reads as a countdown, and off when disabled`() {
        assertEquals("Off", warningLabel(0))
        assertEquals("1 minute before", warningLabel(1))
        assertEquals("5 minutes before", warningLabel(5))
    }

    @Test
    fun `the reward grant reads as time back per cat`() {
        assertEquals("15m back per verified cat", earnedTimeSummary(15))
        assertEquals("1h back per verified cat", earnedTimeSummary(60))
    }

    /**
     * The Cat Detection section used to advertise a hardcoded "85% minimum match" beside the slider
     * that actually sets the gate. This pins the readout to the detector's own mapping.
     */
    @Test
    fun `the confidence readout is derived from the slider position`() {
        assertEquals("90% match", minConfidenceLabel(0f))
        assertEquals("70% match", minConfidenceLabel(0.5f))
        assertEquals("50% match", minConfidenceLabel(1f))
    }

    @Test
    fun `version reads as name and code`() {
        assertEquals("1.0 (1)", versionLabel("1.0", 1))
        assertEquals("2.4.1-alpha (37)", versionLabel("2.4.1-alpha", 37))
    }

    /**
     * The version row shipped a hardcoded "2.4.1-alpha" while the build said 1.0. Pins that the row
     * now renders whatever the build reports, with no placeholder left to drift from it again.
     */
    @Test
    fun `version comes from the build, not a placeholder`() {
        val fromBuild = versionLabel(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)

        assertEquals(fromBuild, state(versionLabel = fromBuild).versionLabel)
        assertEquals("", SettingsUiState().versionLabel)
    }

    /**
     * The production state used to be built as `sample().copy(...)`, so anything the mapper forgot
     * silently inherited preview copy ("Sophia", "Instagram, TikTok, 3 others"). This pins that a
     * bare mapping is empty/denied instead.
     */
    @Test
    fun `unset values do not inherit preview copy`() {
        val ui = state()

        assertEquals("", ui.userName)
        assertEquals("No apps limited yet", ui.appLimitsSummary)
        assertEquals("No limits set yet", ui.dailyLimitLabel)
        assertEquals("No photos yet", ui.photosSummary)
        assertFalse(ui.usageAccessGranted)
        assertFalse(ui.overlayAccessGranted)
        assertFalse(ui.notificationsGranted)
        assertEquals(ThemeMode.SYSTEM, ui.themeMode)
    }
}
