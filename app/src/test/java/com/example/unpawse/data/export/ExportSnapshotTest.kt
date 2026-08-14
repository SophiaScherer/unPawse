package com.example.unpawse.data.export

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportSnapshotTest {

    private fun snapshot(
        monitoredApps: List<ExportMonitoredApp> = listOf(
            ExportMonitoredApp(
                "com.instagram.android", "Instagram", 45, enabled = true,
                weekendLimitMinutes = 90, category = "SOCIAL",
            ),
        ),
        schedules: List<ExportScheduleWindow> = listOf(
            ExportScheduleWindow(1, "Bedtime", null, 22 * 60, 7 * 60, 0b111_1111, enabled = true),
        ),
        usage: List<ExportUsageDay> = listOf(
            ExportUsageDay("2026-07-15", "com.instagram.android", 2_700, 900, blockedCount = 3),
        ),
        unlocks: List<ExportUnlockDay> = listOf(
            ExportUnlockDay("2026-07-15", 24),
        ),
        captures: List<ExportCapture> = listOf(
            ExportCapture(
                "abc-123", 1_700_000_000_000, 0.93f, isBonus = false, isFavorite = true,
                fileName = "abc-123.jpg", earnedMinutes = 15,
            ),
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
            warningMinutes = 5,
            reminderMinutes = 30,
        ),
        monitoredApps = monitoredApps,
        schedules = schedules,
        usage = usage,
        unlocks = unlocks,
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
        assertEquals(5, settings.getInt("warningMinutes"))
        assertEquals(30, settings.getInt("reminderMinutes"))
    }

    @Test
    fun `limits and usage history are carried through`() {
        val root = json()

        val app = root.getJSONArray("monitoredApps").getJSONObject(0)
        assertEquals("com.instagram.android", app.getString("packageName"))
        assertEquals("Instagram", app.getString("appLabel"))
        assertEquals(45, app.getInt("dailyLimitMinutes"))
        assertTrue(app.getBoolean("enabled"))
        assertEquals(90, app.getInt("weekendLimitMinutes"))
        assertEquals("SOCIAL", app.getString("category"))

        val day = root.getJSONArray("usage").getJSONObject(0)
        assertEquals("2026-07-15", day.getString("date"))
        assertEquals(2_700, day.getLong("usedSeconds"))
        assertEquals(900, day.getLong("earnedSeconds"))
        assertEquals(3, day.getInt("blockedCount"))
    }

    /** Unlocks are device-wide, so they are their own array rather than a field on a usage day. */
    @Test
    fun `unlocks export as their own daily series`() {
        val unlockDay = json().getJSONArray("unlocks").getJSONObject(0)

        assertEquals("2026-07-15", unlockDay.getString("date"))
        assertEquals(24, unlockDay.getInt("unlockCount"))
    }

    @Test
    fun `captures export as metadata plus the name of their photo in the bundle`() {
        val capture = json().getJSONArray("captures").getJSONObject(0)

        assertEquals("abc-123", capture.getString("id"))
        assertEquals(1_700_000_000_000, capture.getLong("capturedAt"))
        assertEquals(0.93, capture.getDouble("confidence"), 0.0001)
        assertTrue(capture.getBoolean("isFavorite"))
        assertEquals("abc-123.jpg", capture.getString("fileName"))
        assertEquals(15, capture.getInt("earnedMinutes"))
    }

    /** A capture whose JPEG couldn't be read has no name, and the key drops out entirely. */
    @Test
    fun `a capture with no photo omits the file name`() {
        val root = json(
            snapshot(
                captures = listOf(
                    ExportCapture("no-photo", 1L, 0.9f, isBonus = false, isFavorite = false),
                ),
            ),
        )

        assertFalse(root.getJSONArray("captures").getJSONObject(0).has("fileName"))
    }

    /**
     * The manifest is a file the user may hand to someone else. A path would leak the app's private
     * storage layout while telling the reader nothing, so captures name their photo by bare file
     * name only — the bytes ride in the bundle's `photos/` folder, not in here.
     */
    @Test
    fun `no file paths leak into the manifest`() {
        val raw = buildExportJson(snapshot())

        assertFalse(raw.contains("filePath"))
        assertFalse(raw.contains("/data/"))
        assertFalse(raw.contains("captures/"))

        val capture = json().getJSONArray("captures").getJSONObject(0)
        capture.keys().forEach { key ->
            val value = capture.get(key).toString()
            assertFalse("$key must not contain a path separator", value.contains("/"))
            assertFalse("$key must not contain a path separator", value.contains("\\"))
        }
    }

    @Test
    fun `an empty install still produces a well-formed document`() {
        val root = json(
            snapshot(
                monitoredApps = emptyList(),
                schedules = emptyList(),
                usage = emptyList(),
                captures = emptyList(),
            ),
        )

        assertEquals(0, root.getJSONArray("monitoredApps").length())
        assertEquals(0, root.getJSONArray("usage").length())
        assertEquals(0, root.getJSONArray("captures").length())
        assertEquals(0, root.getJSONArray("schedules").length())
    }

    @Test
    fun `blocking schedules are carried through in the units the app stores`() {
        val window = json().getJSONArray("schedules").getJSONObject(0)

        assertEquals("Bedtime", window.getString("label"))
        assertEquals(22 * 60, window.getInt("startMinuteOfDay"))
        assertEquals(7 * 60, window.getInt("endMinuteOfDay"))
        assertEquals(0b111_1111, window.getInt("daysMask"))
        assertTrue(window.getBoolean("enabled"))
    }

    /**
     * Both nullable fields are written through `put(String, Any?)`, which *removes* the key rather
     * than writing a JSON null. An absent field reads as "not set", which is what null means in both
     * cases — a global window and an app with no weekend override.
     */
    @Test
    fun `unset optional fields are absent rather than null`() {
        val root = json(
            snapshot(
                monitoredApps = listOf(
                    ExportMonitoredApp("com.ig", "Instagram", 45, enabled = true, category = "OTHER"),
                ),
                schedules = listOf(
                    ExportScheduleWindow(1, "Bedtime", null, 22 * 60, 7 * 60, 0b111_1111, enabled = true),
                ),
            ),
        )

        assertFalse(root.getJSONArray("monitoredApps").getJSONObject(0).has("weekendLimitMinutes"))
        assertFalse(root.getJSONArray("schedules").getJSONObject(0).has("packageName"))
        // Category is not one of them: every app has a bucket, even if it's "Other".
        assertEquals("OTHER", root.getJSONArray("monitoredApps").getJSONObject(0).getString("category"))
    }

    @Test
    fun `a per-app window keeps its package name`() {
        val root = json(
            snapshot(
                schedules = listOf(
                    ExportScheduleWindow(2, "School", "com.ig", 9 * 60, 15 * 60, 0b001_1111, enabled = false),
                ),
            ),
        )

        val window = root.getJSONArray("schedules").getJSONObject(0)
        assertEquals("com.ig", window.getString("packageName"))
        assertFalse(window.getBoolean("enabled"))
    }

    /** Names and app labels are user/OEM text; the serializer has to escape, not concatenate. */
    @Test
    fun `quotes and backslashes in text survive a round-trip`() {
        val awkward = """Sophia "The Cat" O'Brien \ Ltd"""

        val settings = json(snapshot(userName = awkward)).getJSONObject("settings")

        assertEquals(awkward, settings.getString("userName"))
    }
}
