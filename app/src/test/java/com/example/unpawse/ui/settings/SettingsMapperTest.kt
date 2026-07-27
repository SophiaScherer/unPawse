package com.example.unpawse.ui.settings

import com.example.unpawse.data.usage.MonitoredApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsMapperTest {

    private fun app(label: String, enabled: Boolean = true) =
        MonitoredApp("com.$label".lowercase(), label, dailyLimitMinutes = 30, enabled = enabled)

    private fun state(
        userName: String = "",
        sensitivity: Float = 0.65f,
        requireLivePhoto: Boolean = false,
        dailySummaryEnabled: Boolean = false,
        monitoredApps: List<MonitoredApp> = emptyList(),
        usageAccessGranted: Boolean = false,
        overlayAccessGranted: Boolean = false,
    ) = toSettingsUiState(
        userName = userName,
        sensitivity = sensitivity,
        requireLivePhoto = requireLivePhoto,
        dailySummaryEnabled = dailySummaryEnabled,
        monitoredApps = monitoredApps,
        usageAccessGranted = usageAccessGranted,
        overlayAccessGranted = overlayAccessGranted,
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
            requireLivePhoto = true,
            dailySummaryEnabled = true,
            monitoredApps = listOf(app("Instagram")),
            usageAccessGranted = true,
            overlayAccessGranted = true,
        )

        assertEquals("Sophia", ui.userName)
        assertEquals(0.8f, ui.sensitivity, 0.0001f)
        assertTrue(ui.requireLivePhoto)
        assertTrue(ui.dailySummaryEnabled)
        assertEquals("Instagram", ui.appLimitsSummary)
        assertTrue(ui.usageAccessGranted)
        assertTrue(ui.overlayAccessGranted)
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
        assertFalse(ui.usageAccessGranted)
        assertFalse(ui.overlayAccessGranted)
        assertFalse(ui.darkMode)
    }
}
