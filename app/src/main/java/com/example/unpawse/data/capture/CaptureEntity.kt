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
    /** See [Capture.isBonus]; decided at insert time and never updated. */
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
    /**
     * Pixel dimensions of the stored JPEG, or 0 when unknown — a row imported from a v6-or-older
     * export document, which carried no shape.
     *
     * Stored rather than measured at render time: the Gallery needs the shape *before* it lays a
     * tile out, and reading it off the file then would mean disk IO per tile and a reflow as each
     * image arrived. Rotation is already baked into the pixels at capture, so a landscape shot is
     * genuinely landscape here.
     */
    val widthPx: Int = 0,
    val heightPx: Int = 0,
)

/** Domain → entity, for an import; every other write builds the entity directly. */
internal fun Capture.toEntity(): CaptureEntity = CaptureEntity(
    id = id,
    filePath = filePath,
    capturedAt = capturedAt,
    confidence = confidence,
    isBonus = isBonus,
    isFavorite = isFavorite,
    earnedMinutes = earnedMinutes,
    widthPx = widthPx,
    heightPx = heightPx,
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
    widthPx = widthPx,
    heightPx = heightPx,
)
