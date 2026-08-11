package com.example.unpawse.data.schedule

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The source of truth for blocking windows. Orchestrates [ScheduleDao] behind domain-shaped calls so
 * callers never touch Room (mirrors `UsageRepository`).
 *
 * Deliberately holds no clock: *whether* a window is active right now is [ScheduleMath]'s job and
 * the enforcement path's, not the store's. This class only knows what windows exist.
 */
class ScheduleRepository(private val dao: ScheduleDao) {

    /** Every window, earliest start first. */
    fun observeWindows(): Flow<List<ScheduleWindow>> =
        dao.observeWindows().map { rows -> rows.map(ScheduleWindowEntity::toDomain) }

    /** Windows that can block [packageName] — its own, plus every global one. */
    fun observeWindowsFor(packageName: String): Flow<List<ScheduleWindow>> =
        dao.observeWindowsFor(packageName).map { rows -> rows.map(ScheduleWindowEntity::toDomain) }

    /** The complete list as a one-shot read. Used by the data export, not by any screen. */
    suspend fun allWindows(): List<ScheduleWindow> =
        dao.allWindows().map(ScheduleWindowEntity::toDomain)

    suspend fun window(id: Long): ScheduleWindow? = dao.window(id)?.toDomain()

    /**
     * Saves a window, inserting when [ScheduleWindow.id] is [NEW_WINDOW_ID] and updating otherwise.
     * Returns the row id so a freshly created window can be identified by the caller.
     */
    suspend fun save(window: ScheduleWindow): Long = dao.upsertWindow(window.toEntity())

    suspend fun setEnabled(id: Long, enabled: Boolean) = dao.setEnabled(id, enabled)

    suspend fun delete(id: Long) = dao.deleteWindow(id)

    /**
     * Drops every window scoped to [packageName]. Called when an app is removed from monitoring —
     * a window pointing at an app unPawse no longer watches can never fire, and leaving it in the
     * list would only be confusing.
     */
    suspend fun deleteWindowsFor(packageName: String) = dao.deleteWindowsFor(packageName)

    /** Drops every window. Used only by the full reset. */
    suspend fun clearAll() = dao.clearWindows()

    companion object {
        /** The id an unsaved window carries; Room autogenerates the real one on insert. */
        const val NEW_WINDOW_ID = 0L
    }
}
