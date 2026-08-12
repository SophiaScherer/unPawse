package com.example.unpawse.data.usage

/**
 * A user-chosen app that unPawse watches, with its daily screen-time budget. Domain model kept
 * decoupled from the Room [MonitoredAppEntity] (same pattern as `Capture`/`CaptureEntity`).
 *
 * @param weekendLimitMinutes Saturday/Sunday override: `null` follows [dailyLimitMinutes],
 * [UNLIMITED_MINUTES] means no cap. See `effectiveLimitMinutes`.
 * @param category which bucket this app's time counts toward in the Stats breakdown. Not nullable
 * here even though the column is — an unclassified app is [AppCategory.OTHER] to every reader.
 */
data class MonitoredApp(
    val packageName: String,
    val appLabel: String,
    val dailyLimitMinutes: Int,
    val enabled: Boolean,
    val weekendLimitMinutes: Int? = null,
    val category: AppCategory = AppCategory.OTHER,
)
