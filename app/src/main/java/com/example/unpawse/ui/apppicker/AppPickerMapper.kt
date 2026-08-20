package com.example.unpawse.ui.apppicker

import com.example.unpawse.data.apps.InstalledApp
import com.example.unpawse.data.schedule.ScheduleWindow
import com.example.unpawse.data.usage.AppCategory
import com.example.unpawse.data.usage.MonitoredApp
import com.example.unpawse.ui.format.formatMinutes

/**
 * Joins the installed-app list with the monitored-app rows into picker items, applying the search
 * filter and the chosen order. Pure and testable, like `GalleryMapper.toGallerySections`.
 *
 * Ordering never floats monitored apps to the top — a row would jump out from under the user's
 * finger the moment they toggled it. [AppSort.MOST_USED] is safe against that same trap for a
 * different reason: toggling an app doesn't change how much it has been used, so nothing moves.
 * Apps that aren't monitored yet show [DEFAULT_LIMIT_MINUTES] as the limit the switch would apply.
 *
 * [dailyAverageSeconds] is `null` when usage access is missing — no figures exist at all — and
 * otherwise authoritative, so a package absent from it really did go unused.
 */
internal fun toAppLimitItems(
    installed: List<InstalledApp>,
    monitored: List<MonitoredApp>,
    searchQuery: String,
    scheduleWindows: List<ScheduleWindow> = emptyList(),
    dailyAverageSeconds: Map<String, Long>? = null,
    sort: AppSort = AppSort.ALPHABETICAL,
): List<AppLimitItem> {
    val monitoredByPackage = monitored.associateBy { it.packageName }
    val query = searchQuery.trim()

    val items = installed
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
                // A stored category is a decision; the platform's is a guess. The decision wins, and
                // an app nobody has classified falls through to OTHER.
                category = row?.category ?: app.category ?: AppCategory.OTHER,
                // A package missing from a non-null map really did go unused; a null map means
                // nothing was measured at all.
                dailyAverageSeconds = dailyAverageSeconds?.let { averages ->
                    averages[app.packageName] ?: 0L
                },
            )
        }

    return items.sortedWith(sort.comparator)
}

/**
 * The tie-break is not cosmetic: it makes the order deterministic (and so testable), and it means a
 * device with no usage history reads as plain A-Z rather than as an arbitrary shuffle.
 */
private val AppSort.comparator: Comparator<AppLimitItem>
    get() = when (this) {
        // Already alphabetical, from `presentableApps`; re-sorting on the same key keeps it so
        // without depending on the provider having done it.
        AppSort.ALPHABETICAL -> compareBy { it.label.lowercase() }
        AppSort.MOST_USED -> compareByDescending<AppLimitItem> { it.dailyAverageSeconds ?: 0L }
            .thenBy { it.label.lowercase() }
    }

/**
 * What a row prints under the app's name, or `null` when there is no figure to print.
 *
 * `<1m` exists because `formatMinutes` floors: an app used for forty seconds a day would otherwise
 * read "0m/day" beside apps that were genuinely never opened, while sorting above them. Same
 * rounding trap AGENTS.md records for the Stats donut's legend, caught here before it shipped.
 */
internal fun dailyAverageLabel(seconds: Long?): String? = when {
    seconds == null -> null
    seconds <= 0L -> "Not used"
    seconds < SECONDS_PER_MINUTE -> "<1m/day"
    else -> "${formatMinutes((seconds / SECONDS_PER_MINUTE).toInt())}/day"
}

private const val SECONDS_PER_MINUTE = 60L

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
