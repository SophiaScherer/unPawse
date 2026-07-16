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
)

internal fun DailyUsageEntity.toDomain(): DailyUsage = DailyUsage(
    packageName = packageName,
    date = date,
    usedSeconds = usedSeconds,
    earnedSeconds = earnedSeconds,
)
