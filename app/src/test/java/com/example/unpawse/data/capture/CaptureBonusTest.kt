package com.example.unpawse.data.capture

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDate
import java.time.ZoneId

/**
 * The read-then-insert seam `CameraViewModel` relies on. Tested here rather than through the
 * ViewModel, which would drag in ML Kit; the ViewModel's own line is a straight call to both.
 */
class CaptureBonusTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 7, 16)
    private val dao = FakeCaptureDao()
    private val storage by lazy { PhotoStorage(tmp.root) }
    private val repo by lazy { CaptureRepository(dao, storage) }

    private fun millisOn(date: LocalDate) =
        date.atStartOfDay(zone).plusHours(12).toInstant().toEpochMilli()

    private suspend fun seedDay(date: LocalDate) {
        dao.insert(
            CaptureEntity(
                id = "seed-$date",
                filePath = storage.save(byteArrayOf(1)),
                capturedAt = millisOn(date),
                confidence = 0.9f,
                isBonus = false,
            ),
        )
    }

    /** What the ViewModel does: decide from the history, then write. */
    private suspend fun capture(): Capture {
        val isBonus = isStreakMilestone(repo.captureDates(zone), today)
        return repo.saveCapture(byteArrayOf(1, 2, 3), confidence = 0.9f, isBonus = isBonus)
    }

    @Test
    fun `a capture landing the milestone is stored as a bonus`() = runBlocking {
        seedDay(today.minusDays(1))
        seedDay(today.minusDays(2))

        val saved = capture()

        assertTrue(dao.findById(saved.id)!!.isBonus)
    }

    @Test
    fun `a second capture the same day is stored as ordinary`() = runBlocking {
        seedDay(today.minusDays(1))
        seedDay(today.minusDays(2))
        seedDay(today)

        val saved = capture()

        assertFalse(dao.findById(saved.id)!!.isBonus)
    }

    /**
     * Regression: reading the history *after* the insert makes today a capture day, so the rule can
     * only ever answer false and no bonus is reachable again. Pinned to the wall-clock date because
     * that is what [CaptureRepository.saveCapture] stamps.
     */
    @Test
    fun `deciding after the insert never yields a bonus`() = runBlocking {
        val wallClockToday = LocalDate.now(zone)
        seedDay(wallClockToday.minusDays(1))
        seedDay(wallClockToday.minusDays(2))
        assertTrue(isStreakMilestone(repo.captureDates(zone), wallClockToday))

        repo.saveCapture(byteArrayOf(1, 2, 3), confidence = 0.9f)

        assertFalse(isStreakMilestone(repo.captureDates(zone), wallClockToday))
    }
}
