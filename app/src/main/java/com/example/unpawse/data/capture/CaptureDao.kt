package com.example.unpawse.data.capture

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Data-access for [CaptureEntity]. The Gallery observes [observeAll]; writes come from capture. */
@Dao
interface CaptureDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(capture: CaptureEntity)

    /** Bulk insert for an import; ids come from the document, so they are preserved. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(captures: List<CaptureEntity>)

    /** Newest first, so the Gallery groups "Today" at the top. */
    @Query("SELECT * FROM captures ORDER BY capturedAt DESC")
    fun observeAll(): Flow<List<CaptureEntity>>

    @Query("SELECT * FROM captures WHERE id = :id")
    suspend fun findById(id: String): CaptureEntity?

    /** Timestamps only: the streak rule needs the dates, not whole rows or their JPEG paths. */
    @Query("SELECT capturedAt FROM captures")
    suspend fun allCapturedAt(): List<Long>

    @Query("UPDATE captures SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean)

    /** Records what a capture bought back, once the reward loop has decided. */
    @Query("UPDATE captures SET earnedMinutes = :minutes WHERE id = :id")
    suspend fun setEarnedMinutes(id: String, minutes: Int)

    /**
     * Non-favorite captures older than [cutoff] (epoch millis). Selected (not bulk-deleted) so the
     * repository can also remove each backing JPEG. Favorites are excluded, so they never expire.
     */
    @Query("SELECT * FROM captures WHERE capturedAt < :cutoff AND isFavorite = 0")
    suspend fun findExpired(cutoff: Long): List<CaptureEntity>

    @Query("DELETE FROM captures WHERE id = :id")
    suspend fun deleteById(id: String)
}
