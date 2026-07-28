package com.example.unpawse.data.export

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportSnapshotTest {

    private fun snapshot(
        monitoredApps: List<ExportMonitoredApp> = listOf(
            ExportMonitoredApp("com.instagram.android", "Instagram", 45, enabled = true),
        ),
        usage: List<ExportUsageDay> = listOf(
            ExportUsageDay("2026-07-15", "com.instagram.android", 2_700, 900),
        ),
        captures: List<ExportCapture> = listOf(
            ExportCapture("abc-123", 1_700_000_000_000, 0.93f, isBonus = false, isFavorite = true),
        ),
        userName: String = "Sophia",
    ) = ExportSnapshot(
        exportedAtMillis = 1_800_000_000_000,
        appVersion = "1.0 (1)",
        settings = ExportSettings(
            userName = userName,
            themeMode = "SYSTEM",
            sensitivity = 0.5f,
            minConfidence = 0.7f,
            earnedMinutesPerCat = 15,
            retentionDays = 30,
            dailySummaryEnabled = false,
        ),
        monitoredApps = monitoredApps,
        usage = usage,
        captures = captures,
    )

    private fun json(snapshot: ExportSnapshot = snapshot()) = JSONObject(buildExportJson(snapshot))

    @Test
    fun `the document is versioned and stamped`() {
        val root = json()

        assertEquals(EXPORT_FORMAT_VERSION, root.getInt("formatVersion"))
        assertEquals(1_800_000_000_000, root.getLong("exportedAt"))
        assertEquals("1.0 (1)", root.getString("appVersion"))
    }

    @Test
    fun `settings round-trip, including the derived confidence gate`() {
        val settings = json().getJSONObject("settings")

        assertEquals("Sophia", settings.getString("userName"))
        assertEquals("SYSTEM", settings.getString("themeMode"))
        assertEquals(0.5, settings.getDouble("sensitivity"), 0.0001)
        assertEquals(0.7, settings.getDouble("minConfidence"), 0.0001)
        assertEquals(15, settings.getInt("earnedMinutesPerCat"))
        assertEquals(30, settings.getInt("retentionDays"))
        assertFalse(settings.getBoolean("dailySummaryEnabled"))
    }

    @Test
    fun `limits and usage history are carried through`() {
        val root = json()

        val app = root.getJSONArray("monitoredApps").getJSONObject(0)
        assertEquals("com.instagram.android", app.getString("packageName"))
        assertEquals("Instagram", app.getString("appLabel"))
        assertEquals(45, app.getInt("dailyLimitMinutes"))
        assertTrue(app.getBoolean("enabled"))

        val day = root.getJSONArray("usage").getJSONObject(0)
        assertEquals("2026-07-15", day.getString("date"))
        assertEquals(2_700, day.getLong("usedSeconds"))
        assertEquals(900, day.getLong("earnedSeconds"))
    }

    @Test
    fun `captures export as metadata`() {
        val capture = json().getJSONArray("captures").getJSONObject(0)

        assertEquals("abc-123", capture.getString("id"))
        assertEquals(1_700_000_000_000, capture.getLong("capturedAt"))
        assertEquals(0.93, capture.getDouble("confidence"), 0.0001)
        assertTrue(capture.getBoolean("isFavorite"))
    }

    /**
     * The export is a file the user may hand to someone else. Photo bytes would bloat it and file
     * paths would leak the app's private storage layout while telling the reader nothing — so
     * [ExportCapture] carries neither, and this pins that it stays that way.
     */
    @Test
    fun `no file paths or image data leak into the export`() {
        val raw = buildExportJson(snapshot())

        assertFalse(raw.contains("filePath"))
        assertFalse(raw.contains(".jpg"))
        assertFalse(raw.contains("/data/"))
        assertFalse(raw.contains("captures/"))
    }

    @Test
    fun `an empty install still produces a well-formed document`() {
        val root = json(
            snapshot(monitoredApps = emptyList(), usage = emptyList(), captures = emptyList()),
        )

        assertEquals(0, root.getJSONArray("monitoredApps").length())
        assertEquals(0, root.getJSONArray("usage").length())
        assertEquals(0, root.getJSONArray("captures").length())
    }

    /** Names and app labels are user/OEM text; the serializer has to escape, not concatenate. */
    @Test
    fun `quotes and backslashes in text survive a round-trip`() {
        val awkward = """Sophia "The Cat" O'Brien \ Ltd"""

        val settings = json(snapshot(userName = awkward)).getJSONObject("settings")

        assertEquals(awkward, settings.getString("userName"))
    }
}
