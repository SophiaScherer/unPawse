package com.example.unpawse.data.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Covers the pure list shaping; the PackageManager query itself needs a device. */
class InstalledAppsProviderTest {

    private val self = "com.example.unpawse"

    @Test
    fun `unPawse itself is never offered as a monitorable app`() {
        val apps = listOf(
            InstalledApp(self, "unPawse"),
            InstalledApp("com.instagram.android", "Instagram"),
        )

        assertEquals(listOf("Instagram"), apps.presentableApps(self).map { it.label })
    }

    @Test
    fun `apps with several launcher activities collapse to one row`() {
        val apps = listOf(
            InstalledApp("com.example.suite", "Suite Mail"),
            InstalledApp("com.example.suite", "Suite Calendar"),
        )

        assertEquals(1, apps.presentableApps(self).size)
    }

    @Test
    fun `results are sorted case-insensitively by label`() {
        val apps = listOf(
            InstalledApp("c", "zulu"),
            InstalledApp("a", "Alpha"),
            InstalledApp("b", "bravo"),
        )

        assertEquals(listOf("Alpha", "bravo", "zulu"), apps.presentableApps(self).map { it.label })
    }

    @Test
    fun `an empty device list stays empty`() {
        assertTrue(emptyList<InstalledApp>().presentableApps(self).isEmpty())
    }
}
