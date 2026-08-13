package com.example.unpawse.data.unlocks

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * How many times the device was unlocked on one day. Keyed by date alone — unlike `daily_usage`
 * this is device-wide, not per-app, which is exactly why it needs its own table: a sentinel package
 * name on the usage rows would leak into the Stats totals, the category breakdown and the export.
 *
 * A daily counter rather than an event log, for the same reasons the usage rows are: the date key
 * gives free daily reset, and nothing needs the individual timestamps. An unbounded event table
 * would need a purge worker to go with it.
 */
@Entity(tableName = "daily_unlocks")
data class DailyUnlocksEntity(
    /** ISO-8601 local date, e.g. "2026-07-15". */
    @PrimaryKey val date: String,
    val unlockCount: Int,
)

/** Domain → entity, for an import. */
internal fun DailyUnlocks.toEntity(): DailyUnlocksEntity = DailyUnlocksEntity(
    date = date,
    unlockCount = unlockCount,
)

/** Mapper kept beside the entity (mirrors `DailyUsageEntity.toDomain`). */
internal fun DailyUnlocksEntity.toDomain(): DailyUnlocks = DailyUnlocks(
    date = date,
    unlockCount = unlockCount,
)
