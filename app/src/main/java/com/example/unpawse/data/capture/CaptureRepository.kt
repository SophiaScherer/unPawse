package com.example.unpawse.data.capture

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * Single entry point the ViewModels use for captures. Orchestrates [PhotoStorage] (the file) and
 * [CaptureDao] (the metadata) so callers never touch either directly.
 */
class CaptureRepository(
    private val dao: CaptureDao,
    private val photoStorage: PhotoStorage,
) {
    /** Bumped when files change without any row changing; see [deleteAllCaptures]. */
    private val storageRevision = MutableStateFlow(0)

    /** Stream of stored captures, newest first, mapped to the domain model. */
    fun observeCaptures(): Flow<List<Capture>> =
        dao.observeAll().map { rows -> rows.map(CaptureEntity::toDomain) }

    /** Local dates that have at least one capture, for the streak rules in [Streaks.kt]. */
    suspend fun captureDates(zone: ZoneId = ZoneId.systemDefault()): Set<LocalDate> =
        dao.allCapturedAt().mapTo(mutableSetOf()) { it.toLocalDate(zone) }

    /**
     * Writes the photo bytes to disk then records its metadata. Only called once a capture has been
     * confirmed as a cat (see the ML layer), so every stored row is a verified cat photo.
     *
     * Stamps [capturedAt] from the wall clock, so a caller deciding [isBonus] against an injected
     * date must pin both together in tests.
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

    /**
     * Writes photo bytes and returns their new path, for an import. The name is [PhotoStorage]'s own
     * UUID, not whatever the archive called the file — a stored path is install-specific, so it has
     * to be re-mapped either way, and an attacker-supplied name never reaches the filesystem.
     */
    suspend fun storePhoto(bytes: ByteArray): String = photoStorage.save(bytes)

    /** Bulk-restores capture rows whose JPEGs are already on disk via [storePhoto]. */
    suspend fun restoreCaptures(captures: List<Capture>) {
        dao.insertAll(captures.map(Capture::toEntity))
        storageRevision.value++
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
     * Records what a capture bought back, called once the reward loop has decided.
     *
     * A second write rather than a parameter on [saveCapture] because the photo is stored *before*
     * the credit is attempted: keeping that order means a crediting failure can't cost the user
     * their photo, and a row that never gets updated reads as "earned nothing", which is true.
     */
    suspend fun recordEarnedMinutes(id: String, minutes: Int) {
        dao.setEarnedMinutes(id, minutes)
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

    /**
     * Deletes every capture, favorites included — the user asked for all of them, and a "delete all"
     * that quietly spared some photos would misreport what it did. Goes through [deleteCapture] so
     * rows and JPEGs come away together.
     */
    suspend fun deleteAllCaptures() {
        dao.observeAll().first().forEach { deleteCapture(it.toDomain()) }
        // Then sweep the directory: a file whose row was lost would otherwise survive a delete-all
        // that told the user it had freed that space.
        photoStorage.deleteAll()
        // Sweeping orphans changes no rows, so the DAO stream won't fire — nudge the size flow
        // ourselves or the screen keeps showing the space it just reported freeing.
        storageRevision.value++
    }

    /**
     * Bytes the stored JPEGs occupy, re-measured whenever the library changes. Driven off the
     * capture stream because size can only be measured, not derived from the row count — a save, a
     * delete or a purge all move it.
     */
    fun observeStorageBytes(): Flow<Long> =
        combine(dao.observeAll(), storageRevision) { _, _ -> photoStorage.totalBytes() }
}
