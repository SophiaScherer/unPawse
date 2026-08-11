package com.example.unpawse.data.schedule

/**
 * A recurring wall-clock window during which apps are blocked outright, whatever budget is left.
 * Domain model kept decoupled from the Room [ScheduleWindowEntity] (same pattern as
 * `MonitoredApp`/`MonitoredAppEntity`).
 *
 * @param packageName the app this window applies to, or `null` for a global window covering every
 * monitored app. Global windows are how "bedtime" is expressed without repeating it per app.
 * @param startMinuteOfDay inclusive start, minutes since local midnight (0..1439).
 * @param endMinuteOfDay exclusive end, same units. A value at or below [startMinuteOfDay] means the
 * window wraps past midnight — see [isActiveAt] for the day-of-week rule that follows from that.
 * @param daysMask which days the window *starts* on, as bits from [dayBit].
 */
data class ScheduleWindow(
    val id: Long,
    val label: String,
    val packageName: String?,
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
    val daysMask: Int,
    val enabled: Boolean,
) {
    /** True for a window that covers every monitored app rather than one package. */
    val isGlobal: Boolean get() = packageName == null
}
