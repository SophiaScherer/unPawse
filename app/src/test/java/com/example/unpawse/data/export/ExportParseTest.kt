package com.example.unpawse.data.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [parseExportJson] is [buildExportJson]'s inverse, and has to stay one across format versions. */
class ExportParseTest {

    private val snapshot = ExportSnapshot(
        exportedAtMillis = 1_800_000_000_000,
        appVersion = "1.0 (1)",
        settings = ExportSettings(
            userName = """Sophia "The Cat" O'Brien \ Ltd""",
            themeMode = "DARK",
            sensitivity = 0.65f,
            minConfidence = 0.72f,
            earnedMinutesPerCat = 20,
            retentionDays = 30,
            dailySummaryEnabled = true,
            warningMinutes = 10,
            reminderMinutes = 15,
        ),
        monitoredApps = listOf(
            ExportMonitoredApp("com.ig", "Instagram", 45, enabled = true, weekendLimitMinutes = 90, category = "SOCIAL"),
            ExportMonitoredApp("com.yt", "YouTube", 60, enabled = false, category = "OTHER"),
        ),
        schedules = listOf(
            ExportScheduleWindow(1, "Bedtime", null, 22 * 60, 7 * 60, 0b111_1111, enabled = true),
            ExportScheduleWindow(2, "School", "com.ig", 9 * 60, 15 * 60, 0b001_1111, enabled = false),
        ),
        usage = listOf(ExportUsageDay("2026-07-15", "com.ig", 2_700, 900, blockedCount = 3)),
        unlocks = listOf(ExportUnlockDay("2026-07-15", 24)),
        captures = listOf(
            ExportCapture("abc", 1_700_000_000_000, 0.93f, isBonus = true, isFavorite = true, fileName = "abc.jpg", earnedMinutes = 15, widthPx = 1600, heightPx = 1200),
            ExportCapture("def", 1_700_000_001_000, 0.81f, isBonus = false, isFavorite = false),
        ),
    )

    @Test
    fun `a document we just wrote parses back to the same snapshot`() {
        assertEquals(snapshot, parseExportJson(buildExportJson(snapshot)))
    }

    @Test
    fun `an empty install round-trips`() {
        val empty = snapshot.copy(
            monitoredApps = emptyList(),
            schedules = emptyList(),
            usage = emptyList(),
            unlocks = emptyList(),
            captures = emptyList(),
        )

        assertEquals(empty, parseExportJson(buildExportJson(empty)))
    }

    /**
     * The trap: `optInt` would read an absent key as 0, and 0 here means "blocked from the first
     * second" rather than "weekends follow the everyday limit".
     */
    @Test
    fun `an absent weekend limit parses as null, not zero`() {
        val json = """
            {"formatVersion":6,"settings":{},
             "monitoredApps":[{"packageName":"com.ig","appLabel":"Instagram","dailyLimitMinutes":45}]}
        """.trimIndent()

        assertNull(parseExportJson(json)!!.monitoredApps.single().weekendLimitMinutes)
    }

    /** Same shape, and here the absence means the window is global rather than per-app. */
    @Test
    fun `an absent schedule package name parses as null`() {
        val json = """
            {"formatVersion":6,"settings":{},
             "schedules":[{"id":1,"label":"Bedtime","startMinuteOfDay":1320,"endMinuteOfDay":420,"daysMask":127}]}
        """.trimIndent()

        assertNull(parseExportJson(json)!!.schedules.single().packageName)
    }

    @Test
    fun `a v5 document reads with no photo names and no earned minutes`() {
        val json = """
            {"formatVersion":5,"exportedAt":1,"appVersion":"0.9 (1)","settings":{"userName":"Sophia"},
             "captures":[{"id":"old","capturedAt":1700,"confidence":0.9,"isBonus":false,"isFavorite":true}]}
        """.trimIndent()

        val capture = parseExportJson(json)!!.captures.single()
        assertNull(capture.fileName)
        assertEquals(0, capture.earnedMinutes)
        assertTrue(capture.isFavorite)
        // No shape recorded before v7; 0 reads as "unknown" and the Gallery falls back to a default.
        assertEquals(0, capture.widthPx)
        assertEquals(0, capture.heightPx)
    }

    /** A v5 document has no warning/reminder keys, so they fall back to the shipped defaults. */
    @Test
    fun `missing settings fall back to defaults rather than zero`() {
        val parsed = parseExportJson("""{"formatVersion":5,"settings":{"userName":"Sophia"}}""")!!

        assertEquals("Sophia", parsed.settings.userName)
        assertEquals(5, parsed.settings.warningMinutes)
        assertEquals(30, parsed.settings.retentionDays)
        assertEquals(15, parsed.settings.earnedMinutesPerCat)
    }

    @Test
    fun `a document from a newer build is refused`() {
        assertNull(parseExportJson("""{"formatVersion":99,"settings":{}}"""))
        assertEquals(99, exportFormatVersionOf("""{"formatVersion":99,"settings":{}}"""))
    }

    @Test
    fun `anything that is not an export is refused`() {
        assertNull(parseExportJson("not json at all"))
        assertNull(parseExportJson("{}"))
        assertNull(parseExportJson("""{"formatVersion":6}"""))
        assertNull(exportFormatVersionOf("not json at all"))
    }

    /** A partially corrupt document should restore what it can rather than nothing at all. */
    @Test
    fun `elements missing their key are skipped, the rest survive`() {
        val json = """
            {"formatVersion":6,"settings":{},
             "captures":[{"capturedAt":1},{"id":"good","capturedAt":2}],
             "monitoredApps":[{"appLabel":"No package"},{"packageName":"com.ig","appLabel":"Instagram"}],
             "usage":[{"date":"2026-07-15"},{"date":"2026-07-15","packageName":"com.ig"}]}
        """.trimIndent()

        val parsed = parseExportJson(json)!!
        assertEquals(listOf("good"), parsed.captures.map { it.id })
        assertEquals(listOf("com.ig"), parsed.monitoredApps.map { it.packageName })
        assertEquals(1, parsed.usage.size)
    }
}
