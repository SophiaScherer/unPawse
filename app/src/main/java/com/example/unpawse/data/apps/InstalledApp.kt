package com.example.unpawse.data.apps

import com.example.unpawse.data.usage.AppCategory

/**
 * A launchable app installed on the device, as offered in the app picker. Deliberately carries no
 * icon: icons are `Drawable`s (a UI concern) and are loaded lazily per visible row instead of
 * eagerly for every installed app — see `ui/apppicker/AppIcon.kt`.
 *
 * [category] is the platform's own declaration, or `null` when it didn't make one. It is only ever a
 * *default* for a newly monitored app; once the row exists, the stored category wins.
 */
data class InstalledApp(
    val packageName: String,
    val label: String,
    val category: AppCategory? = null,
)

/**
 * The platform's declarations alone, keyed by package, as
 * [com.example.unpawse.data.usage.resolveCategories] wants them.
 *
 * Lives here rather than in `data/usage` so the dependency keeps pointing one way: apps knows about
 * categories, categories know nothing about apps.
 */
fun List<InstalledApp>.platformCategories(): Map<String, AppCategory?> =
    associate { it.packageName to it.category }
