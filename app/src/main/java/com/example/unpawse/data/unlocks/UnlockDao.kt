package com.example.unpawse.data.unlocks

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Room access for the daily unlock counter. An abstract class rather than an interface for the same
 * reason as `UsageDao`: [addUnlock] has to be a `@Transaction` combining insert-if-absent with an
 * in-place `+=`, so the count can't be lost to a read-modify-write race with a second unlock.
 */
@Dao
abstract class UnlockDao {

    /**
     * Unlocks across a closed date range. ISO-8601 dates sort lexicographically in the same order as
     * chronologically, so `BETWEEN` works directly — same trick as `observeUsageBetween`.
     */
    @Query("SELECT * FROM daily_unlocks WHERE date BETWEEN :startDate AND :endDate")
    abstract fun observeUnlocksBetween(startDate: String, endDate: String): Flow<List<DailyUnlocksEntity>>

    /** Every row, oldest first — the whole history, for the data export. */
    @Query("SELECT * FROM daily_unlocks ORDER BY date")
    abstract suspend fun allUnlocks(): List<DailyUnlocksEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertIfAbsent(row: DailyUnlocksEntity)

    @Query("UPDATE daily_unlocks SET unlockCount = unlockCount + 1 WHERE date = :date")
    abstract suspend fun incrementUnlocks(date: String)

    @Query("DELETE FROM daily_unlocks")
    abstract suspend fun clearUnlocks()

    /** Bulk insert for an import; [addUnlock] only ever increments today. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertUnlocks(rows: List<DailyUnlocksEntity>)

    @Transaction
    open suspend fun addUnlock(date: String) {
        insertIfAbsent(DailyUnlocksEntity(date, unlockCount = 0))
        incrementUnlocks(date)
    }
}
