package com.example.unpawse.ui.apppicker

import com.example.unpawse.data.apps.InstalledApp
import com.example.unpawse.data.schedule.ScheduleWindow
import com.example.unpawse.data.usage.MonitoredApp

/**
 * Joins the installed-app list with the monitored-app rows into picker items, applying the search
 * filter. Pure and testable, like `GalleryMapper.toGallerySections`.
 *
 * Ordering deliberately stays the provider's alphabetical order rather than floating monitored apps
 * to the top — otherwise a row would jump out from under the user's finger the moment they toggle it.
 * Apps that aren't monitored yet show [DEFAULT_LIMIT_MINUTES] as the limit the switch would apply.
 */
internal fun toAppLimitItems(
    installed: List<InstalledApp>,
    monitored: List<MonitoredApp>,
    searchQuery: String,
    scheduleWindows: List<ScheduleWindow> = emptyList(),
): List<AppLimitItem> {
    val monitoredByPackage = monitored.associateBy { it.packageName }
    val query = searchQuery.trim()

    return installed
        .filter { query.isEmpty() || it.label.contains(query, ignoreCase = true) }
        .map { app ->
            val row = monitoredByPackage[app.packageName]
            AppLimitItem(
                packageName = app.packageName,
                label = app.label,
                monitored = row?.enabled == true,
                dailyLimitMinutes = row?.dailyLimitMinutes ?: DEFAULT_LIMIT_MINUTES,
                weekendLimitMinutes = row?.weekendLimitMinutes,
                scheduleSummary = scheduleSummaryFor(app.packageName, scheduleWindows),
            )
        }
}

/** How many window names to spell out before collapsing the rest into a count. */
private const val WINDOWS_SHOWN = 2

/**
 * Names the enabled windows that would block [packageName] — its own plus every global one. Paused
 * windows are left out for the same reason a switched-off app is left out of the Settings summary:
 * it isn't blocking anything right now, so advertising it would be wrong.
 */
internal fun scheduleSummaryFor(packageName: String, windows: List<ScheduleWindow>): String {
    val covering = windows.filter {
        it.enabled && (it.packageName == null || it.packageName == packageName)
    }
    if (covering.isEmpty()) return NO_SCHEDULES_SUMMARY

    val shown = covering.take(WINDOWS_SHOWN).joinToString(", ") { it.label }
    val others = covering.size - WINDOWS_SHOWN
    return when {
        others <= 0 -> shown
        others == 1 -> "$shown, 1 more"
        else -> "$shown, $others more"
    }
}
