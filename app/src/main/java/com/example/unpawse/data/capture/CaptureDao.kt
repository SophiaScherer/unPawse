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

    /** Newest first, so the Gallery groups "Today" at the top. */
    @Query("SELECT * FROM captures ORDER BY capturedAt DESC")
    fun observeAll(): Flow<List<CaptureEntity>>

    @Query("SELECT * FROM captures WHERE id = :id")
    suspend fun findById(id: String): CaptureEntity?

    @Query("UPDATE captures SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean)

    /**
     * Non-favorite captures older than [cutoff] (epoch millis). Selected (not bulk-deleted) so the
     * repository can also remove each backing JPEG. Favorites are excluded, so they never expire.
     */
    @Query("SELECT * FROM captures WHERE capturedAt < :cutoff AND isFavorite = 0")
    suspend fun findExpired(cutoff: Long): List<CaptureEntity>

    @Query("DELETE FROM captures WHERE id = :id")
    suspend fun deleteById(id: String)
}
