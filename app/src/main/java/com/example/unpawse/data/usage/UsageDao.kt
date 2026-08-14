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

    /** A null [minutes] clears the override, putting weekends back on the everyday budget. */
    @Query("UPDATE monitored_apps SET weekendLimitMinutes = :minutes WHERE packageName = :packageName")
    abstract suspend fun setWeekendLimit(packageName: String, minutes: Int?)

    /** [category] is an `AppCategory.name`; null puts the app back to unclassified. */
    @Query("UPDATE monitored_apps SET category = :category WHERE packageName = :packageName")
    abstract suspend fun setCategory(packageName: String, category: String?)

    @Query("DELETE FROM monitored_apps WHERE packageName = :packageName")
    abstract suspend fun removeMonitoredApp(packageName: String)

    @Query("DELETE FROM monitored_apps")
    abstract suspend fun clearMonitoredApps()

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

    /** Every row, oldest first — the whole history, for the data export. */
    @Query("SELECT * FROM daily_usage ORDER BY date, packageName")
    abstract suspend fun allUsage(): List<DailyUsageEntity>

    @Query("DELETE FROM daily_usage")
    abstract suspend fun clearUsage()

    /**
     * Bulk insert for an import. The everyday writers all key on *today* and increment, so none of
     * them can express a historical row.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertUsage(rows: List<DailyUsageEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertUsageIfAbsent(row: DailyUsageEntity)

    @Query("UPDATE daily_usage SET usedSeconds = usedSeconds + :seconds WHERE packageName = :packageName AND date = :date")
    abstract suspend fun addUsedSeconds(packageName: String, date: String, seconds: Long)

    /**
     * Credits earned seconds and stamps when it happened, in one statement — the cooldown is only
     * enforceable if the timestamp can't be lost between the two.
     */
    @Query(
        "UPDATE daily_usage SET earnedSeconds = earnedSeconds + :seconds, lastEarnedAtMillis = :atMillis " +
            "WHERE packageName = :packageName AND date = :date",
    )
    abstract suspend fun addEarnedSecondsAt(
        packageName: String,
        date: String,
        seconds: Long,
        atMillis: Long,
    )

    @Query("UPDATE daily_usage SET blockedCount = blockedCount + 1 WHERE packageName = :packageName AND date = :date")
    abstract suspend fun incrementBlockedCount(packageName: String, date: String)

    @Transaction
    open suspend fun addUsage(packageName: String, date: String, seconds: Long) {
        insertUsageIfAbsent(DailyUsageEntity(packageName, date, usedSeconds = 0, earnedSeconds = 0))
        addUsedSeconds(packageName, date, seconds)
    }

    @Transaction
    open suspend fun addEarned(packageName: String, date: String, seconds: Long, atMillis: Long) {
        insertUsageIfAbsent(DailyUsageEntity(packageName, date, usedSeconds = 0, earnedSeconds = 0))
        addEarnedSecondsAt(packageName, date, seconds, atMillis)
    }

    /**
     * Records one block raised over [packageName]. Same insert-then-increment shape as [addUsage],
     * so it inherits the same atomicity — the tracker credits usage and evaluates the block on the
     * same tick, so the row almost always exists already, but "almost" isn't a guarantee.
     */
    @Transaction
    open suspend fun addBlock(packageName: String, date: String) {
        insertUsageIfAbsent(DailyUsageEntity(packageName, date, usedSeconds = 0, earnedSeconds = 0))
        incrementBlockedCount(packageName, date)
    }
}
