package com.example.unpawse.data.usage

/**
 * A user-chosen app that unPawse watches, with its daily screen-time budget. Domain model kept
 * decoupled from the Room [MonitoredAppEntity] (same pattern as `Capture`/`CaptureEntity`).
 *
 * @param weekendLimitMinutes Saturday/Sunday override: `null` follows [dailyLimitMinutes],
 * [UNLIMITED_MINUTES] means no cap. See `effectiveLimitMinutes`.
 */
data class MonitoredApp(
    val packageName: String,
    val appLabel: String,
    val dailyLimitMinutes: Int,
    val enabled: Boolean,
    val weekendLimitMinutes: Int? = null,
)
