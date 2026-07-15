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

    @Query("DELETE FROM captures WHERE id = :id")
    suspend fun deleteById(id: String)
}
