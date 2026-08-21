package com.example.unpawse.ui.schedules

import com.example.unpawse.data.schedule.EVERY_DAY_MASK
import com.example.unpawse.data.schedule.ScheduleWindow
import com.example.unpawse.data.schedule.WEEKDAYS_MASK
import com.example.unpawse.data.schedule.WEEKENDS_MASK
import com.example.unpawse.data.schedule.daysIn
import com.example.unpawse.data.usage.MonitoredApp
import com.example.unpawse.ui.format.formatMinuteOfDay
import com.example.unpawse.ui.format.countLabel
import com.example.unpawse.ui.format.formatTimeRange
import java.time.format.TextStyle
import java.util.Locale

/**
 * Pure shaping for the Schedules screen, so every string it renders is unit-tested without a device
 * (the convention every other screen's mapper follows). [locale] is a parameter rather than read
 * from the default so the tests don't depend on the machine they run on.
 */

/** How many day names to spell out before falling back to a count. */
private const val DAYS_SHOWN = 3

/** Human name for a days bitmask: "Every day", "Weekdays", "Mon, Wed, Fri", "4 days a week". */
internal fun daysLabel(daysMask: Int, locale: Locale = Locale.getDefault()): String {
    val days = daysIn(daysMask)
    return when {
        days.isEmpty() -> "Never"
        daysMask == EVERY_DAY_MASK -> "Every day"
        daysMask == WEEKDAYS_MASK -> "Weekdays"
        daysMask == WEEKENDS_MASK -> "Weekends"
        days.size <= DAYS_SHOWN -> days.joinToString(", ") { it.getDisplayName(TextStyle.SHORT, locale) }
        else -> "${days.size} days a week"
    }
}

/** Which app a window covers, by label — or [ALL_APPS_LABEL] for a global one. */
internal fun scopeLabel(packageName: String?, monitoredApps: List<MonitoredApp>): String {
    if (packageName == null) return ALL_APPS_LABEL
    // An app can stop being monitored while a window still points at it; naming the package is
    // more honest than pretending the window is global.
    return monitoredApps.firstOrNull { it.packageName == packageName }?.appLabel ?: packageName
}

/** The choices in the editor's "Applies to" picker: every monitored app, plus the global option. */
internal fun appOptions(monitoredApps: List<MonitoredApp>): List<ScheduleAppOption> =
    listOf(ScheduleAppOption(null, ALL_APPS_LABEL)) +
        monitoredApps.filter { it.enabled }.map { ScheduleAppOption(it.packageName, it.appLabel) }

internal fun ScheduleWindow.toDraft() = ScheduleDraft(
    id = id,
    label = label,
    packageName = packageName,
    startMinuteOfDay = startMinuteOfDay,
    endMinuteOfDay = endMinuteOfDay,
    daysMask = daysMask,
    enabled = enabled,
)

internal fun ScheduleDraft.toWindow() = ScheduleWindow(
    id = id,
    label = effectiveLabel,
    packageName = packageName,
    startMinuteOfDay = startMinuteOfDay,
    endMinuteOfDay = endMinuteOfDay,
    daysMask = daysMask,
    enabled = enabled,
)

internal fun toScheduleItem(
    window: ScheduleWindow,
    monitoredApps: List<MonitoredApp>,
    locale: Locale = Locale.getDefault(),
) = ScheduleItem(
    draft = window.toDraft(),
    timeRange = formatTimeRange(window.startMinuteOfDay, window.endMinuteOfDay, locale),
    daysLabel = daysLabel(window.daysMask, locale),
    scopeLabel = scopeLabel(window.packageName, monitoredApps),
    // An end at or before the start runs past midnight; the list says so, because "10:00 PM –
    // 7:00 AM" otherwise reads as a window that already closed.
    isOvernight = window.endMinuteOfDay <= window.startMinuteOfDay,
)

internal fun toSchedulesUiState(
    windows: List<ScheduleWindow>,
    monitoredApps: List<MonitoredApp>,
    locale: Locale = Locale.getDefault(),
) = SchedulesUiState(
    windows = windows.map { toScheduleItem(it, monitoredApps, locale) },
    appOptions = appOptions(monitoredApps),
    isLoading = false,
)

/**
 * Subtitle for the Settings row, e.g. "2 schedules · Bedtime 10:00 PM". Names the earliest active
 * window because that's the one a user is most likely checking for.
 */
internal fun schedulesSummary(windows: List<ScheduleWindow>, locale: Locale = Locale.getDefault()): String {
    if (windows.isEmpty()) return "No schedules yet"

    val active = windows.filter { it.enabled }
    if (active.isEmpty()) return "All schedules paused"

    val first = active.first()
    return "${countLabel(active.size, "schedule")} · ${first.label} ${formatMinuteOfDay(first.startMinuteOfDay, locale)}"
}
