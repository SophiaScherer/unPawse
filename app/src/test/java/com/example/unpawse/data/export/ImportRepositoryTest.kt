package com.example.unpawse.data.export

import com.example.unpawse.data.ResetRepository
import com.example.unpawse.data.capture.CaptureRepository
import com.example.unpawse.data.capture.FakeCaptureDao
import com.example.unpawse.data.capture.PhotoStorage
import com.example.unpawse.data.schedule.FakeScheduleDao
import com.example.unpawse.data.schedule.ScheduleRepository
import com.example.unpawse.data.unlocks.FakeUnlockDao
import com.example.unpawse.data.unlocks.UnlockRepository
import com.example.unpawse.data.usage.FakeUsageDao
import com.example.unpawse.data.usage.UsageRepository
import com.example.unpawse.service.BlockSession
import com.example.unpawse.service.FocusSession
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ImportRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val captureDao = FakeCaptureDao()
    private val usageDao = FakeUsageDao()
    private val scheduleDao = FakeScheduleDao()
    private val unlockDao = FakeUnlockDao()

    private val storage by lazy { PhotoStorage(tmp.root) }
    private val captures by lazy { CaptureRepository(captureDao, storage) }
    private val usage = UsageRepository(usageDao)
    private val schedules = ScheduleRepository(scheduleDao)
    private val unlocks = UnlockRepository(unlockDao)

    private var settingsCleared = false
    private var appliedSettings: ExportSettings? = null

    private val reset by lazy {
        ResetRepository(
            usage = usage,
            schedules = schedules,
            captures = captures,
            unlocks = unlocks,
            focusSession = FocusSession(),
            blockSession = BlockSession(),
            clearSettings = { settingsCleared = true },
        )
    }

    private val repo by lazy {
        ImportRepository(
            usage = usage,
            unlocks = unlocks,
            schedules = schedules,
            captures = captures,
            reset = reset,
            applySettings = { appliedSettings = it },
            // Every case here drives the InputStream overload directly; the uri path is device-only.
            openDocument = { null },
        )
    }

    private val snapshot = ExportSnapshot(
        exportedAtMillis = 1_800_000_000_000,
        appVersion = "1.0 (1)",
        settings = ExportSettings("Sophia", "DARK", 0.65f, 0.72f, 20, 30, true, 10, 15),
        monitoredApps = listOf(
            ExportMonitoredApp("com.ig", "Instagram", 45, enabled = true, weekendLimitMinutes = 90, category = "SOCIAL"),
            ExportMonitoredApp("com.yt", "YouTube", 60, enabled = false, category = "OTHER"),
        ),
        schedules = listOf(
            ExportScheduleWindow(1, "Bedtime", null, 22 * 60, 7 * 60, 0b111_1111, enabled = true),
        ),
        usage = listOf(ExportUsageDay("2026-07-15", "com.ig", 2_700, 900, blockedCount = 3)),
        unlocks = listOf(ExportUnlockDay("2026-07-15", 24)),
        captures = listOf(
            ExportCapture("abc", 1_700_000_000_000, 0.93f, isBonus = true, isFavorite = true, fileName = "abc.jpg", earnedMinutes = 15),
        ),
    )

    private fun bundle(
        snap: ExportSnapshot = snapshot,
        photos: Map<String, ByteArray> = mapOf("abc.jpg" to byteArrayOf(1, 2, 3)),
    ): ByteArray = ByteArrayOutputStream().also { out ->
        writeBundle(
            out = out,
            manifestJson = buildExportJson(snap),
            photos = photos.map { (name, bytes) -> PhotoSource(name) { ByteArrayInputStream(bytes) } },
        )
    }.toByteArray()

    private suspend fun seedExistingData() {
        usage.setLimit("com.old", "Old app", 10)
        schedules.save(
            com.example.unpawse.data.schedule.ScheduleWindow(
                id = 0, label = "Old window", packageName = null,
                startMinuteOfDay = 60, endMinuteOfDay = 120, daysMask = 0b111_1111, enabled = true,
            ),
        )
        captures.saveCapture(byteArrayOf(9), confidence = 0.5f)
    }

    @Test
    fun `a bundle restores every store`() = runBlocking {
        val result = repo.importFrom(ByteArrayInputStream(bundle()))

        assertEquals(ImportResult.Restored(captures = 1, skippedCaptures = 0), result)
        assertEquals(snapshot.settings, appliedSettings)

        val apps = usage.monitoredApps().associateBy { it.packageName }
        assertEquals(45, apps.getValue("com.ig").dailyLimitMinutes)
        assertEquals(90, apps.getValue("com.ig").weekendLimitMinutes)
        assertEquals("SOCIAL", apps.getValue("com.ig").category.name)
        assertEquals(false, apps.getValue("com.yt").enabled)

        val day = usage.allUsage().single()
        assertEquals(2_700, day.usedSeconds)
        assertEquals(3, day.blockedCount)

        assertEquals(24, unlocks.allUnlocks().single().unlockCount)
        assertEquals("Bedtime", schedules.allWindows().single().label)
    }

    /** Ids and the flags hanging off them are preserved; only the file path is re-mapped. */
    @Test
    fun `a restored capture keeps its metadata and gains a real photo`() = runBlocking {
        repo.importFrom(ByteArrayInputStream(bundle()))

        val capture = captures.observeCaptures().first().single()
        assertEquals("abc", capture.id)
        assertEquals(1_700_000_000_000, capture.capturedAt)
        assertEquals(15, capture.earnedMinutes)
        assertTrue(capture.isBonus)
        assertTrue(capture.isFavorite)
        assertTrue("the JPEG should be on disk", File(capture.filePath).exists())
        assertEquals(listOf<Byte>(1, 2, 3), File(capture.filePath).readBytes().toList())
    }

    @Test
    fun `existing data is erased before the restore`() = runBlocking {
        seedExistingData()

        repo.importFrom(ByteArrayInputStream(bundle()))

        assertTrue(settingsCleared)
        assertNull(usage.monitoredApps().find { it.packageName == "com.old" })
        assertEquals(listOf("Bedtime"), schedules.allWindows().map { it.label })
        assertEquals(listOf("abc"), captures.observeCaptures().first().map { it.id })
    }

    /** No photo means a permanently broken gallery tile, so the row is skipped and counted. */
    @Test
    fun `a capture whose photo is missing from the bundle is skipped`() = runBlocking {
        val result = repo.importFrom(ByteArrayInputStream(bundle(photos = emptyMap())))

        assertEquals(ImportResult.Restored(captures = 0, skippedCaptures = 1), result)
        assertTrue(captures.observeCaptures().first().isEmpty())
    }

    /** A v5 document carries no photos at all, so every capture is honestly reported as skipped. */
    @Test
    fun `a legacy json document restores everything but the photos`() = runBlocking {
        val legacy = """
            {"formatVersion":5,"exportedAt":1,"appVersion":"0.9 (1)",
             "settings":{"userName":"Sophia","themeMode":"DARK","sensitivity":0.65,
                         "earnedMinutesPerCat":20,"retentionDays":30,"dailySummaryEnabled":true},
             "monitoredApps":[{"packageName":"com.ig","appLabel":"Instagram","dailyLimitMinutes":45,"enabled":true}],
             "usage":[{"date":"2026-07-15","packageName":"com.ig","usedSeconds":2700,"earnedSeconds":900}],
             "unlocks":[{"date":"2026-07-15","unlockCount":24}],
             "captures":[{"id":"old","capturedAt":1700,"confidence":0.9,"isBonus":false,"isFavorite":true}]}
        """.trimIndent()

        val result = repo.importFrom(ByteArrayInputStream(legacy.toByteArray()))

        assertEquals(ImportResult.Restored(captures = 0, skippedCaptures = 1), result)
        assertEquals("Sophia", appliedSettings?.userName)
        assertEquals(45, usage.monitoredApps().single().dailyLimitMinutes)
        assertEquals(24, unlocks.allUnlocks().single().unlockCount)
    }

    /**
     * The single most important property here: a wipe followed by a failed parse would destroy the
     * user's data on behalf of a corrupt file.
     */
    @Test
    fun `an unreadable file leaves the store completely untouched`() = runBlocking {
        seedExistingData()
        val before = captures.observeCaptures().first().single()

        val result = repo.importFrom(ByteArrayInputStream("not an export".toByteArray()))

        assertEquals(ImportResult.Unreadable, result)
        assertEquals(false, settingsCleared)
        assertNotNull(usage.monitoredApps().find { it.packageName == "com.old" })
        assertEquals(listOf("Old window"), schedules.allWindows().map { it.label })
        assertEquals(listOf(before.id), captures.observeCaptures().first().map { it.id })
        assertTrue(File(before.filePath).exists())
    }

    @Test
    fun `a truncated bundle leaves the store untouched`() = runBlocking {
        seedExistingData()
        val truncated = bundle().copyOf(8)

        val result = repo.importFrom(ByteArrayInputStream(truncated))

        assertEquals(ImportResult.Unreadable, result)
        assertEquals(false, settingsCleared)
        assertNotNull(usage.monitoredApps().find { it.packageName == "com.old" })
    }

    /** A zip that isn't ours has no manifest, so nothing is erased and no photos are written. */
    @Test
    fun `a foreign zip is refused without touching anything`() = runBlocking {
        seedExistingData()
        val foreign = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry("notes.txt"))
                zip.write("hello".toByteArray())
                zip.closeEntry()
            }
        }.toByteArray()

        val result = repo.importFrom(ByteArrayInputStream(foreign))

        assertEquals(ImportResult.Unreadable, result)
        assertEquals(false, settingsCleared)
        assertNotNull(usage.monitoredApps().find { it.packageName == "com.old" })
    }

    @Test
    fun `a document from a newer build is refused by version, not silently accepted`() = runBlocking {
        seedExistingData()
        val newer = """{"formatVersion":99,"settings":{}}"""

        val result = repo.importFrom(ByteArrayInputStream(newer.toByteArray()))

        assertEquals(ImportResult.TooNew(99), result)
        assertEquals(false, settingsCleared)
    }

    @Test
    fun `an empty file is refused`() = runBlocking {
        assertEquals(ImportResult.Unreadable, repo.importFrom(ByteArrayInputStream(byteArrayOf())))
    }
}
