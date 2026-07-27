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

    @Test
    fun `storage size reflects the files actually on disk`() = runBlocking {
        assertEquals(0L, repo.observeStorageBytes().first())

        seed("a", capturedAt = 1_000)
        seed("b", capturedAt = 2_000)

        // Each seed writes a 3-byte JPEG.
        assertEquals(6L, repo.observeStorageBytes().first())
    }

    @Test
    fun `deleteCaptureById removes the row and the file`() = runBlocking {
        val capture = seed("gone", capturedAt = 1_000)

        repo.deleteCaptureById("gone")

        assertTrue(dao.all().isEmpty())
        assertFalse(File(capture.filePath).exists())
    }
}
