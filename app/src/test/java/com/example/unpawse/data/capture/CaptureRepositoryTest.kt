package com.example.unpawse.data.capture

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/**
 * Exercises [CaptureRepository]'s favorite + retention orchestration against an in-memory DAO and a
 * real temp-folder [PhotoStorage], so we assert both the metadata rows and the backing JPEGs.
 */
class CaptureRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val dao = FakeCaptureDao()
    private val storage by lazy { PhotoStorage(tmp.root) }
    private val repo by lazy { CaptureRepository(dao, storage) }

    /** Creates a backing JPEG and its row with a chosen age/favorite state; returns the domain model. */
    private suspend fun seed(id: String, capturedAt: Long, favorite: Boolean = false): Capture {
        val path = storage.save(byteArrayOf(1, 2, 3))
        val entity = CaptureEntity(
            id = id,
            filePath = path,
            capturedAt = capturedAt,
            confidence = 0.9f,
            isBonus = false,
            isFavorite = favorite,
        )
        dao.insert(entity)
        return entity.toDomain()
    }

    @Test
    fun `a fresh capture is recorded as having earned nothing`() = runBlocking {
        val capture = repo.saveCapture(byteArrayOf(1, 2, 3), confidence = 0.9f)

        assertEquals(0, capture.earnedMinutes)
    }

    @Test
    fun `recordEarnedMinutes stamps what the capture bought back`() = runBlocking {
        val capture = repo.saveCapture(byteArrayOf(1, 2, 3), confidence = 0.9f)

        repo.recordEarnedMinutes(capture.id, 15)

        assertEquals(15, dao.findById(capture.id)?.earnedMinutes)
    }

    @Test
    fun `captureDates collapses same-day captures into one date`() = runBlocking {
        val zone = ZoneId.of("UTC")
        val noon = LocalDate.of(2026, 7, 16).atStartOfDay(zone).plusHours(12).toInstant().toEpochMilli()
        seed("morning", capturedAt = noon - 3_600_000)
        seed("evening", capturedAt = noon + 3_600_000)
        seed("next day", capturedAt = noon + 86_400_000)

        assertEquals(
            setOf(LocalDate.of(2026, 7, 16), LocalDate.of(2026, 7, 17)),
            repo.captureDates(zone),
        )
    }

    /** A capture just before UTC midnight is the *next* day in a zone ahead of it. */
    @Test
    fun `captureDates honours the zone it is given`() = runBlocking {
        val lateUtc = LocalDate.of(2026, 7, 16).atStartOfDay(ZoneId.of("UTC"))
            .plusHours(23).toInstant().toEpochMilli()
        seed("late", capturedAt = lateUtc)

        assertEquals(setOf(LocalDate.of(2026, 7, 16)), repo.captureDates(ZoneId.of("UTC")))
        assertEquals(setOf(LocalDate.of(2026, 7, 17)), repo.captureDates(ZoneId.of("Australia/Sydney")))
    }

    @Test
    fun `purgeExpired removes old non-favorites and their files`() = runBlocking {
        val cutoff = 10_000L
        val oldPlain = seed("old", capturedAt = cutoff - 1)
        val recentPlain = seed("recent", capturedAt = cutoff + 1)

        repo.purgeExpired(cutoff)

        assertEquals(listOf("recent"), dao.all().map { it.id })
        assertFalse("expired JPEG should be deleted", File(oldPlain.filePath).exists())
        assertTrue("recent JPEG should remain", File(recentPlain.filePath).exists())
    }

    @Test
    fun `purgeExpired never deletes favorites, even when old`() = runBlocking {
        val cutoff = 10_000L
        val oldFavorite = seed("fav", capturedAt = cutoff - 5_000, favorite = true)

        repo.purgeExpired(cutoff)

        assertEquals(listOf("fav"), dao.all().map { it.id })
        assertTrue("favorite JPEG should survive the purge", File(oldFavorite.filePath).exists())
    }

    @Test
    fun `setFavorite flips the flag`() = runBlocking {
        seed("c", capturedAt = 1_000)
        assertFalse(dao.all().single().isFavorite)

        repo.setFavorite("c", true)
        assertTrue(dao.all().single().isFavorite)

        repo.setFavorite("c", false)
        assertFalse(dao.all().single().isFavorite)
    }

    /**
     * Unlike the purge, "Delete all photos" takes favorites too — the confirm dialog says so, and a
     * bulk delete that quietly spared some photos would misreport what it did.
     */
    @Test
    fun `deleteAllCaptures removes every row and file, favorites included`() = runBlocking {
        val plain = seed("plain", capturedAt = 1_000)
        val favorite = seed("fav", capturedAt = 2_000, favorite = true)

        repo.deleteAllCaptures()

        assertTrue(dao.all().isEmpty())
        assertFalse(File(plain.filePath).exists())
        assertFalse("delete all means all", File(favorite.filePath).exists())
    }

    /**
     * A JPEG can outlive its row — a crash between the write and the insert, or a destructive Room
     * migration. Delete-all reports the space it frees, so it has to free all of it.
     */
    @Test
    fun `deleteAllCaptures also removes files left without a row`() = runBlocking {
        val orphan = File(storage.save(byteArrayOf(9, 9, 9)))
        seed("tracked", capturedAt = 1_000)

        repo.deleteAllCaptures()

        assertFalse("orphaned JPEG should be swept", orphan.exists())
        assertEquals(0L, repo.observeStorageBytes().first())
    }

    @Test
    fun `storage size reflects the files actually on disk`() = runBlocking {
        assertEquals(0L, repo.observeStorageBytes().first())

        seed("a", capturedAt = 1_000)
        seed("b", capturedAt = 2_000)

        // Each seed writes a 3-byte JPEG.
        assertEquals(6L, repo.observeStorageBytes().first())
    }

    // --- Reconciliation ------------------------------------------------------------------------

    @Test
    fun `reconcileMissingFiles drops rows whose JPEG has gone and reports how many`() = runBlocking {
        val kept = seed("kept", capturedAt = 1_000)
        val lost = seed("lost", capturedAt = 2_000)
        File(lost.filePath).delete()

        assertEquals(1, repo.reconcileMissingFiles())

        assertEquals(listOf("kept"), dao.all().map { it.id })
        assertTrue("the surviving JPEG is untouched", File(kept.filePath).exists())
    }

    /**
     * The purge spares favorites because the photo is still there; this cannot, because it isn't.
     * A starred row pointing at nothing is the most misleading kind, so the difference is deliberate.
     */
    @Test
    fun `reconcileMissingFiles does not spare favorites`() = runBlocking {
        val favorite = seed("fav", capturedAt = 1_000, favorite = true)
        File(favorite.filePath).delete()

        assertEquals(1, repo.reconcileMissingFiles())
        assertTrue(dao.all().isEmpty())
    }

    /**
     * One-directional on purpose: saveCapture writes the JPEG and only then inserts its row, so a
     * sweep in the other direction would delete a capture in flight.
     */
    @Test
    fun `reconcileMissingFiles leaves a file that has no row`() = runBlocking {
        val orphan = File(storage.save(byteArrayOf(9, 9, 9)))

        assertEquals(0, repo.reconcileMissingFiles())
        assertTrue("an orphaned JPEG is not this sweep's business", orphan.exists())
    }

    @Test
    fun `reconcileMissingFiles leaves an intact library alone`() = runBlocking {
        seed("a", capturedAt = 1_000)
        seed("b", capturedAt = 2_000)

        assertEquals(0, repo.reconcileMissingFiles())
        assertEquals(2, dao.all().size)
    }

    @Test
    fun `deleteCaptureById removes the row and the file`() = runBlocking {
        val capture = seed("gone", capturedAt = 1_000)

        repo.deleteCaptureById("gone")

        assertTrue(dao.all().isEmpty())
        assertFalse(File(capture.filePath).exists())
    }
}
