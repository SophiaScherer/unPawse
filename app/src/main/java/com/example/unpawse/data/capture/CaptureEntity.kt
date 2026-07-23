package com.example.unpawse.data.capture

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Room row for a single capture. Maps 1:1 to [Capture] via [toDomain]. */
@Entity(tableName = "captures")
data class CaptureEntity(
    @PrimaryKey val id: String,
    val filePath: String,
    val capturedAt: Long,
    val confidence: Float,
    val isBonus: Boolean,
    val isFavorite: Boolean = false,
)

/** Entity → domain mapping kept next to the entity so both evolve together. */
internal fun CaptureEntity.toDomain(): Capture = Capture(
    id = id,
    filePath = filePath,
    capturedAt = capturedAt,
    confidence = confidence,
    isBonus = isBonus,
    isFavorite = isFavorite,
)
