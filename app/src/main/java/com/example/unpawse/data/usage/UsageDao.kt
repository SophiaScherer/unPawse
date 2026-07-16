package com.example.unpawse.data.usage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Room access for monitored apps + daily usage. An abstract class (not an interface) so the
 * increment helpers can be `@Transaction`s that combine an insert-if-absent with an in-place
 * `+=` update — atomic accrual without a read-modify-write race.
 */
@Dao
abstract class UsageDao {

    // --- Monitored apps -------------------------------------------------------------------------

    @Query("SELECT * FROM monitored_apps ORDER BY appLabel COLLATE NOCASE")
    abstract fun observeMonitoredApps(): Flow<List<MonitoredAppEntity>>

    @Query("SELECT * FROM monitored_apps WHERE packageName = :packageName LIMIT 1")
    abstract suspend fun monitoredApp(packageName: String): MonitoredAppEntity?

    @Upsert
    abstract suspend fun upsertMonitoredApp(app: MonitoredAppEntity)

    @Query("UPDATE monitored_apps SET enabled = :enabled WHERE packageName = :packageName")
    abstract suspend fun setEnabled(packageName: String, enabled: Boolean)

    @Query("DELETE FROM monitored_apps WHERE packageName = :packageName")
    abstract suspend fun removeMonitoredApp(packageName: String)

    // --- Daily usage ----------------------------------------------------------------------------

    @Query("SELECT * FROM daily_usage WHERE date = :date")
    abstract fun observeUsageForDate(date: String): Flow<List<DailyUsageEntity>>

    /**
     * Usage across a closed date range. Dates are ISO-8601 (`yyyy-MM-dd`), which sorts
     * lexicographically in the same order as chronologically — so `BETWEEN` works directly.
     */
    @Query("SELECT * FROM daily_usage WHERE date BETWEEN :startDate AND :endDate")
    abstract fun observeUsageBetween(startDate: String, endDate: String): Flow<List<DailyUsageEntity>>

    @Query("SELECT * FROM daily_usage WHERE packageName = :packageName AND date = :date LIMIT 1")
    abstract suspend fun usageFor(packageName: String, date: String): DailyUsageEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertUsageIfAbsent(row: DailyUsageEntity)

    @Query("UPDATE daily_usage SET usedSeconds = usedSeconds + :seconds WHERE packageName = :packageName AND date = :date")
    abstract suspend fun addUsedSeconds(packageName: String, date: String, seconds: Long)

    @Query("UPDATE daily_usage SET earnedSeconds = earnedSeconds + :seconds WHERE packageName = :packageName AND date = :date")
    abstract suspend fun addEarnedSeconds(packageName: String, date: String, seconds: Long)

    @Transaction
    open suspend fun addUsage(packageName: String, date: String, seconds: Long) {
        insertUsageIfAbsent(DailyUsageEntity(packageName, date, usedSeconds = 0, earnedSeconds = 0))
        addUsedSeconds(packageName, date, seconds)
    }

    @Transaction
    open suspend fun addEarned(packageName: String, date: String, seconds: Long) {
        insertUsageIfAbsent(DailyUsageEntity(packageName, date, usedSeconds = 0, earnedSeconds = 0))
        addEarnedSeconds(packageName, date, seconds)
    }
}
