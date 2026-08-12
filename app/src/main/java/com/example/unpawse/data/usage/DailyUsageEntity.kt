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
    /**
     * How many times a block was raised over this app today — one per breach, not per tick.
     *
     * Unlike [lastEarnedAtMillis] this **is** history rather than policy bookkeeping, so it does
     * reach [DailyUsage], the Stats mapper and the export. It rides on this row rather than a
     * `block_events` table because a block is a per-app, per-day fact keyed exactly like usage, so
     * the composite key already gives it free daily reset and atomic accrual, and Stats already
     * loads these rows.
     */
    val blockedCount: Int = 0,
)

internal fun DailyUsageEntity.toDomain(): DailyUsage = DailyUsage(
    packageName = packageName,
    date = date,
    usedSeconds = usedSeconds,
    earnedSeconds = earnedSeconds,
    blockedCount = blockedCount,
)
