package com.example.unpawse.data.capture

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * Single entry point the ViewModels use for captures. Orchestrates [PhotoStorage] (the file) and
 * [CaptureDao] (the metadata) so callers never touch either directly.
 */
class CaptureRepository(
    private val dao: CaptureDao,
    private val photoStorage: PhotoStorage,
) {
    /** Stream of stored captures, newest first, mapped to the domain model. */
    fun observeCaptures(): Flow<List<Capture>> =
        dao.observeAll().map { rows -> rows.map(CaptureEntity::toDomain) }

    /**
     * Writes the photo bytes to disk then records its metadata. Only called once a capture has been
     * confirmed as a cat (see the ML layer), so every stored row is a verified cat photo.
     */
    suspend fun saveCapture(bytes: ByteArray, confidence: Float, isBonus: Boolean = false): Capture {
        val filePath = photoStorage.save(bytes)
        val entity = CaptureEntity(
            id = UUID.randomUUID().toString(),
            filePath = filePath,
            capturedAt = System.currentTimeMillis(),
            confidence = confidence,
            isBonus = isBonus,
        )
        dao.insert(entity)
        return entity.toDomain()
    }

    /** Removes both the metadata row and the backing file. */
    suspend fun deleteCapture(capture: Capture) {
        dao.deleteById(capture.id)
        photoStorage.delete(capture.filePath)
    }

    /** Removes a capture by id (row + backing file). No-op if the id no longer exists. */
    suspend fun deleteCaptureById(id: String) {
        dao.findById(id)?.let { deleteCapture(it.toDomain()) }
    }

    /** Stars/unstars a capture. Favorites are exempt from [purgeExpired]. */
    suspend fun setFavorite(id: String, favorite: Boolean) {
        dao.setFavorite(id, favorite)
    }

    /**
     * Deletes every non-favorite capture older than [cutoffMillis] (epoch millis), removing both the
     * row and its JPEG via [deleteCapture]. Favorites are excluded by the query, so "favorites are
     * never auto-deleted" holds. Called on a schedule by
     * [com.example.unpawse.service.CaptureRetentionWorker].
     */
    suspend fun purgeExpired(cutoffMillis: Long) {
        dao.findExpired(cutoffMillis).forEach { deleteCapture(it.toDomain()) }
    }
}
