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
    /**
     * Bonus minutes this capture actually bought back; 0 when it bought none — no block was armed,
     * or the cap/cooldown refused it.
     *
     * Stored rather than inferred because nothing else can reconstruct it: `daily_usage` records a
     * running per-app total with no link back to the photo that caused it. Without this column the
     * Home feed could only *assume* every cat earned time, which is exactly what it used to do.
     */
    val earnedMinutes: Int = 0,
)

/** Entity → domain mapping kept next to the entity so both evolve together. */
internal fun CaptureEntity.toDomain(): Capture = Capture(
    id = id,
    filePath = filePath,
    capturedAt = capturedAt,
    confidence = confidence,
    isBonus = isBonus,
    isFavorite = isFavorite,
    earnedMinutes = earnedMinutes,
)
