package com.example.unpawse.data.schedule

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Room access for blocking windows. A plain interface, unlike [com.example.unpawse.data.usage.UsageDao]
 * — there is no accrual to make atomic here, only whole-row writes the user makes one at a time.
 *
 * Every read is ordered the same way (earliest start first, id as the tiebreak) so the enforcement
 * lookup and the list screen agree on which of two overlapping windows "wins".
 */
@Dao
interface ScheduleDao {

    @Query("SELECT * FROM schedule_windows ORDER BY startMinuteOfDay, id")
    fun observeWindows(): Flow<List<ScheduleWindowEntity>>

    @Query(
        "SELECT * FROM schedule_windows WHERE packageName IS NULL OR packageName = :packageName " +
            "ORDER BY startMinuteOfDay, id",
    )
    fun observeWindowsFor(packageName: String): Flow<List<ScheduleWindowEntity>>

    @Query("SELECT * FROM schedule_windows ORDER BY startMinuteOfDay, id")
    suspend fun allWindows(): List<ScheduleWindowEntity>

    @Query("SELECT * FROM schedule_windows WHERE id = :id LIMIT 1")
    suspend fun window(id: Long): ScheduleWindowEntity?

    /** Returns the row id, which is the generated one for an insert — the caller may need it. */
    @Upsert
    suspend fun upsertWindow(window: ScheduleWindowEntity): Long

    @Query("UPDATE schedule_windows SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM schedule_windows WHERE id = :id")
    suspend fun deleteWindow(id: Long)

    /** Drops windows scoped to one app; used when that app stops being monitored. */
    @Query("DELETE FROM schedule_windows WHERE packageName = :packageName")
    suspend fun deleteWindowsFor(packageName: String)

    @Query("DELETE FROM schedule_windows")
    suspend fun clearWindows()
}
