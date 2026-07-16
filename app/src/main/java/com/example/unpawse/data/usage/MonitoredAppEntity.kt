package com.example.unpawse.data.usage

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Room row for a monitored app; the package name is its stable identity. */
@Entity(tableName = "monitored_apps")
data class MonitoredAppEntity(
    @PrimaryKey val packageName: String,
    val appLabel: String,
    val dailyLimitMinutes: Int,
    val enabled: Boolean,
)

/** Mapper kept beside the entity (mirrors `CaptureEntity.toDomain`). */
internal fun MonitoredAppEntity.toDomain(): MonitoredApp = MonitoredApp(
    packageName = packageName,
    appLabel = appLabel,
    dailyLimitMinutes = dailyLimitMinutes,
    enabled = enabled,
)
