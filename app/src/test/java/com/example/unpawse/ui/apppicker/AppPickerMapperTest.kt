package com.example.unpawse.ui.apppicker

import com.example.unpawse.data.apps.InstalledApp
import com.example.unpawse.data.usage.MonitoredApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPickerMapperTest {

    private val installed = listOf(
        InstalledApp("com.instagram.android", "Instagram"),
        InstalledApp("com.spotify.music", "Spotify"),
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
}
