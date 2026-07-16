package com.example.unpawse.ui.settings

import com.example.unpawse.data.usage.MonitoredApp
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsSummaryTest {

    private fun app(label: String, enabled: Boolean = true) =
        MonitoredApp("com.$label".lowercase(), label, dailyLimitMinutes = 30, enabled = enabled)

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
}
