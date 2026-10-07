package com.example.unpawse.data.export

import com.example.unpawse.data.FakeTransactor
import com.example.unpawse.data.ResetRepository
import com.example.unpawse.data.capture.Capture
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
import kotlin.random.Random

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

    private val transactor = FakeTransactor(
        listOf(captureDao::checkpoint, usageDao::checkpoint, scheduleDao::checkpoint, unlockDao::checkpoint),
    )
    private val focusSession = FocusSession()
    private var failSettingsWrite = false

    private val stagingDir by lazy { File(tmp.root, "staging") }

    private var settingsCleared = false
    private var appliedSettings: ExportSettings? = null

    private val reset by lazy {
        ResetRepository(
            usage = usage,
            schedules = schedules,
            captures = captures,
            unlocks = unlocks,
            focusSession = focusSession,
            blockSession = BlockSession(),
            transactor = transactor,
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
            transactor = transactor,
            applySettings = {
                if (failSettingsWrite) throw IllegalStateException("disk full")
                appliedSettings = it
            },
            // Every case here drives the InputStream overload directly; the uri path is device-only.
            openDocument = { null },
            stagingDir = stagingDir,
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

        // Preferences are replaced by the one imported write rather than cleared separately.
        assertEquals(snapshot.settings, appliedSettings)
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

        assertEquals(ImportResult.Damaged, result)
        assertEquals(false, settingsCleared)
        assertNotNull(usage.monitoredApps().find { it.packageName == "com.old" })
    }

    /** A zip that isn't ours has no manifest, so nothing is erased and no photos are written. */
    /**
     * The case "parse before wipe" used to miss: the manifest parses, so the old code erased
     * everything, then found nothing after it. The cut sits exactly on an entry boundary, which a
     * streaming reader can't tell apart from the end of a smaller archive.
     */
    @Test
    fun `a bundle cut off right after the manifest leaves every store untouched`() = runBlocking {
        seedExistingData()
        val before = captures.observeCaptures().first().single()
        val whole = bundle()
        val truncated = whole.copyOf(whole.localHeaderOffset(1))

        val result = repo.importFrom(ByteArrayInputStream(truncated))

        assertEquals(ImportResult.Damaged, result)
        assertUntouched(before)
    }

    @Test
    fun `a bundle cut off mid-photo leaves every store untouched`() = runBlocking {
        seedExistingData()
        val before = captures.observeCaptures().first().single()
        val whole = bundle(photos = mapOf("abc.jpg" to Random(7).nextBytes(8_000)))
        val truncated = whole.copyOf(whole.localHeaderOffset(1) + 2_000)

        val result = repo.importFrom(ByteArrayInputStream(truncated))

        assertEquals(ImportResult.Damaged, result)
        assertUntouched(before)
    }

    /** The wipe and the restore share a transaction, so a failed commit takes back both. */
    @Test
    fun `a failed commit leaves every store untouched`() = runBlocking {
        seedExistingData()
        focusSession.start(durationMinutes = 30)
        val before = captures.observeCaptures().first().single()
        transactor.failCommit = true

        val result = repo.importFrom(ByteArrayInputStream(bundle()))

        assertEquals(ImportResult.Failed, result)
        assertUntouched(before)
        assertTrue("the focus session only stops once the import commits", focusSession.isActive())
    }

    /** By then the data has committed, so saying "nothing was changed" would be false. */
    @Test
    fun `a settings write failing after the commit is reported rather than hidden`() = runBlocking {
        seedExistingData()
        failSettingsWrite = true

        val result = repo.importFrom(ByteArrayInputStream(bundle()))

        assertEquals(ImportResult.Restored(captures = 1, skippedCaptures = 0, settingsRestored = false), result)
        assertEquals(listOf("Bedtime"), schedules.allWindows().map { it.label })
    }

    /** The old library's JPEGs go after the commit, and only the imported one is left. */
    @Test
    fun `a restore leaves exactly the imported photos on disk`() = runBlocking {
        seedExistingData()

        repo.importFrom(ByteArrayInputStream(bundle()))

        val restored = captures.observeCaptures().first().single()
        assertEquals(listOf(File(restored.filePath).name), File(tmp.root, "captures").list()?.toList())
        assertTrue("staging should be emptied", stagingDir.list().isNullOrEmpty())
    }

    /** Rows, files and settings all as [seedExistingData] left them, and no orphan JPEGs. */
    private suspend fun assertUntouched(before: Capture) {
        assertEquals(false, settingsCleared)
        assertNull(appliedSettings)
        assertEquals(listOf("com.old"), usage.monitoredApps().map { it.packageName })
        assertEquals(listOf("Old window"), schedules.allWindows().map { it.label })
        assertEquals(listOf(before.id), captures.observeCaptures().first().map { it.id })
        assertEquals(listOf<Byte>(9), File(before.filePath).readBytes().toList())
        assertEquals(
            "no orphan files beside the old photo",
            listOf(File(before.filePath).name),
            File(tmp.root, "captures").list()?.toList(),
        )
        assertTrue("staging should be emptied", stagingDir.list().isNullOrEmpty())
    }

    /** Where entry [n] starts: the offset of its local file header signature. */
    private fun ByteArray.localHeaderOffset(n: Int): Int {
        var seen = -1
        for (i in 0..size - 4) {
            if (this[i] == 0x50.toByte() && this[i + 1] == 0x4B.toByte() &&
                this[i + 2] == 0x03.toByte() && this[i + 3] == 0x04.toByte()
            ) {
                if (++seen == n) return i
            }
        }
        error("the archive has no entry $n")
    }

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
