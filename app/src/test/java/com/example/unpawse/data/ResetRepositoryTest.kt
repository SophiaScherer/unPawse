package com.example.unpawse.data

import com.example.unpawse.data.capture.CaptureEntity
import com.example.unpawse.data.capture.CaptureRepository
import com.example.unpawse.data.capture.FakeCaptureDao
import com.example.unpawse.data.capture.PhotoStorage
import com.example.unpawse.data.schedule.EVERY_DAY_MASK
import com.example.unpawse.data.schedule.FakeScheduleDao
import com.example.unpawse.data.schedule.ScheduleRepository
import com.example.unpawse.data.schedule.ScheduleWindow
import com.example.unpawse.data.unlocks.FakeUnlockDao
import com.example.unpawse.data.unlocks.UnlockRepository
import com.example.unpawse.data.usage.FakeUsageDao
import com.example.unpawse.data.usage.UsageRepository
import com.example.unpawse.service.BlockSession
import com.example.unpawse.service.FocusSession
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate
import kotlin.time.Duration.Companion.minutes

/**
 * The full wipe behind Settings → "Delete all data". The preference store needs a `Context`, so it
 * is injected as a lambda and asserted as "was asked to clear"; everything else is exercised for
 * real against the shared fakes and a temp-folder [PhotoStorage] — including the in-memory session
 * state, which is the part a database-only reset would miss.
 */
class ResetRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val usageDao = FakeUsageDao()
    private val captureDao = FakeCaptureDao()
    private val storage by lazy { PhotoStorage(tmp.root) }
    private val usage = UsageRepository(usageDao, today = { LocalDate.of(2026, 7, 27) })
    private val schedules = ScheduleRepository(FakeScheduleDao())
    private val unlocks = UnlockRepository(FakeUnlockDao(), today = { LocalDate.of(2026, 7, 27) })
    private val captures by lazy { CaptureRepository(captureDao, storage) }
    private val focusSession = FocusSession()
    private val blockSession = BlockSession()

    private var settingsCleared = false

    private val reset by lazy {
        ResetRepository(
            usage = usage,
            schedules = schedules,
            captures = captures,
            unlocks = unlocks,
            focusSession = focusSession,
            blockSession = blockSession,
            clearSettings = { settingsCleared = true },
        )
    }

    private suspend fun seedEverything(): String {
        usage.setLimit("com.ig", "Instagram", dailyLimitMinutes = 30)
        usage.addUsage("com.ig", 10.minutes)
        usage.addEarnedMinutes("com.ig", 15)

        val path = storage.save(byteArrayOf(1, 2, 3))
        captureDao.insert(
            CaptureEntity(
                id = "cat-1",
                filePath = path,
                capturedAt = 1_000,
                confidence = 0.9f,
                isBonus = false,
                isFavorite = true,
            ),
        )

        schedules.save(
            ScheduleWindow(
                id = ScheduleRepository.NEW_WINDOW_ID,
                label = "Bedtime",
                packageName = null,
                startMinuteOfDay = 22 * 60,
                endMinuteOfDay = 7 * 60,
                daysMask = EVERY_DAY_MASK,
                enabled = true,
            ),
        )

        repeat(3) { unlocks.recordUnlock() }

        focusSession.start(durationMinutes = 30)
        blockSession.start("com.ig")
        return path
    }

    @Test
    fun `every store is emptied`() = runBlocking {
        val photoPath = seedEverything()

        reset.eraseEverything()

        assertTrue("monitored apps", usage.monitoredApps().isEmpty())
        assertTrue("usage history", usage.allUsage().isEmpty())
        assertTrue("capture rows", captureDao.all().isEmpty())
        assertFalse("capture JPEG", File(photoPath).exists())
        assertTrue("preferences", settingsCleared)
        assertTrue("blocking schedules", schedules.allWindows().isEmpty())
        assertTrue("unlock history", unlocks.allUnlocks().isEmpty())
    }

    /**
     * A window left behind would keep blocking apps the app no longer has any record of monitoring —
     * the same failure mode as a surviving focus session, just persisted rather than in memory.
     */
    @Test
    fun `blocking schedules do not survive the wipe`() = runBlocking {
        seedEverything()
        assertEquals("precondition: one window", 1, schedules.allWindows().size)

        reset.eraseEverything()

        assertTrue(schedules.allWindows().isEmpty())
    }

    /**
     * A focus session hard-blocks every monitored app and lives outside the database. Left running
     * after a wipe it would keep blocking apps the app no longer has any record of.
     */
    @Test
    fun `a running focus session does not survive the wipe`() = runBlocking {
        seedEverything()
        assertTrue("precondition: focus is running", focusSession.isActive())

        reset.eraseEverything()

        assertFalse(focusSession.isActive())
        assertNull(focusSession.endTimeMillis.value)
    }

    /** An armed block session is a debt owed against a limit that no longer exists. */
    @Test
    fun `an armed block session is cleared`() = runBlocking {
        seedEverything()
        assertEquals("com.ig", blockSession.armed.value?.packageName)

        reset.eraseEverything()

        assertNull(blockSession.armed.value)
    }

    @Test
    fun `favourite photos are deleted too`() = runBlocking {
        val photoPath = seedEverything()

        reset.eraseEverything()

        assertTrue("the seeded capture was a favourite", captureDao.all().isEmpty())
        assertFalse(File(photoPath).exists())
    }

    @Test
    fun `erasing an already-empty install is a no-op rather than a crash`() = runBlocking {
        reset.eraseEverything()

        assertTrue(usage.allUsage().isEmpty())
        assertTrue(captureDao.all().isEmpty())
    }
}
