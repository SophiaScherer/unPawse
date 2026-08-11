package com.example.unpawse.data.usage

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room row for a monitored app; the package name is its stable identity.
 *
 * [weekendLimitMinutes] is the optional Saturday/Sunday override: `null` means weekends use
 * [dailyLimitMinutes], and [UNLIMITED_MINUTES] means no cap at all. Storing it as a nullable column
 * keeps "I never thought about weekends" distinguishable from "weekends are deliberately the same",
 * which matters for what the picker shows.
 */
@Entity(tableName = "monitored_apps")
data class MonitoredAppEntity(
    @PrimaryKey val packageName: String,
    val appLabel: String,
    val dailyLimitMinutes: Int,
    val enabled: Boolean,
    val weekendLimitMinutes: Int? = null,
)

/** Mapper kept beside the entity (mirrors `CaptureEntity.toDomain`). */
internal fun MonitoredAppEntity.toDomain(): MonitoredApp = MonitoredApp(
    packageName = packageName,
    appLabel = appLabel,
    dailyLimitMinutes = dailyLimitMinutes,
    enabled = enabled,
    weekendLimitMinutes = weekendLimitMinutes,
)
