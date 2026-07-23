package com.example.unpawse.data.capture

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
        val capture = seed("c", capturedAt = 1_000)
        assertFalse(dao.all().single().isFavorite)

        repo.setFavorite(capture, true)
        assertTrue(dao.all().single().isFavorite)

        repo.setFavorite(capture, false)
        assertFalse(dao.all().single().isFavorite)
    }
}
