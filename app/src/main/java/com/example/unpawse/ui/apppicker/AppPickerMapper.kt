package com.example.unpawse.ui.apppicker

import com.example.unpawse.data.apps.InstalledApp
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
            )
        }
}
