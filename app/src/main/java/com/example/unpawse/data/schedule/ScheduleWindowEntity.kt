package com.example.unpawse.data.schedule

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room row for one blocking window. Unlike `monitored_apps` there is no natural key — a user can
 * keep several windows for the same app — so the id is generated.
 *
 * [packageName] is indexed because the per-app lookup runs on every enforcement tick.
 */
@Entity(
    tableName = "schedule_windows",
    indices = [Index("packageName")],
)
data class ScheduleWindowEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    /** null = applies to every monitored app; otherwise scoped to one package. */
    val packageName: String?,
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
    val daysMask: Int,
    val enabled: Boolean,
)

/** Mapper kept beside the entity (mirrors `MonitoredAppEntity.toDomain`). */
internal fun ScheduleWindowEntity.toDomain(): ScheduleWindow = ScheduleWindow(
    id = id,
    label = label,
    packageName = packageName,
    startMinuteOfDay = startMinuteOfDay,
    endMinuteOfDay = endMinuteOfDay,
    daysMask = daysMask,
    enabled = enabled,
)

internal fun ScheduleWindow.toEntity(): ScheduleWindowEntity = ScheduleWindowEntity(
    id = id,
    label = label,
    packageName = packageName,
    startMinuteOfDay = startMinuteOfDay,
    endMinuteOfDay = endMinuteOfDay,
    daysMask = daysMask,
    enabled = enabled,
)
