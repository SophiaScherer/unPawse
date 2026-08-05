package com.example.unpawse.data.usage

import androidx.room.Entity

/**
 * Room row for per-app, per-day usage. Composite key `(packageName, date)` gives free daily reset:
 * a new day is simply a new row, so yesterday's usage never counts against today.
 */
@Entity(tableName = "daily_usage", primaryKeys = ["packageName", "date"])
data class DailyUsageEntity(
    val packageName: String,
    val date: String,
    val usedSeconds: Long,
    val earnedSeconds: Long,
    /**
     * When this app last earned bonus minutes, epoch millis; 0 when it hasn't yet today. Drives the
     * reward cooldown, and lives on this row so it survives process death and resets at midnight
     * for free — the same two properties [earnedSeconds] gets from the composite key.
     *
     * Deliberately absent from [DailyUsage]: it is policy bookkeeping, not usage history, so it
     * stays out of the domain model, the Stats/Home mappers, and the data export.
     */
    val lastEarnedAtMillis: Long = 0L,
)

internal fun DailyUsageEntity.toDomain(): DailyUsage = DailyUsage(
    packageName = packageName,
    date = date,
    usedSeconds = usedSeconds,
    earnedSeconds = earnedSeconds,
)
