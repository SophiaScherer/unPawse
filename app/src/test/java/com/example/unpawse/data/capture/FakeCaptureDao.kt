package com.example.unpawse.data.capture

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * In-memory [CaptureDao] for tests, mirroring the real query semantics (newest-first observe,
 * favorite update, expired selection excluding favorites). The observe flow emits a single snapshot
 * — enough for the pure-logic tests here; use the suspend reads for anything order-sensitive.
 */
internal class FakeCaptureDao : CaptureDao {

    private val rows = mutableMapOf<String, CaptureEntity>()

    override suspend fun insert(capture: CaptureEntity) {
        rows[capture.id] = capture
    }

    override fun observeAll(): Flow<List<CaptureEntity>> =
        flowOf(rows.values.sortedByDescending { it.capturedAt })

    override suspend fun findById(id: String): CaptureEntity? = rows[id]

    override suspend fun setFavorite(id: String, favorite: Boolean) {
        rows[id]?.let { rows[id] = it.copy(isFavorite = favorite) }
    }

    override suspend fun findExpired(cutoff: Long): List<CaptureEntity> =
        rows.values.filter { it.capturedAt < cutoff && !it.isFavorite }

    override suspend fun deleteById(id: String) {
        rows.remove(id)
    }

    /** Test helper: current rows, used to assert what survived a purge. */
    fun all(): List<CaptureEntity> = rows.values.toList()
}
