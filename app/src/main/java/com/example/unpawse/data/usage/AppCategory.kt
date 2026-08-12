package com.example.unpawse.data.usage

import android.content.pm.ApplicationInfo

/**
 * What kind of app this is, for the Stats usage breakdown. The mockup's donut splits screen time
 * three ways; [OTHER] is the fourth bucket this app adds, because a real device always has apps the
 * platform never classified and the user hasn't got round to.
 *
 * Stored on [MonitoredAppEntity] as [name] rather than an ordinal — a String column survives a
 * reorder of this enum and reads correctly in a `sqlite3` dump.
 */
enum class AppCategory(val label: String) {
    SOCIAL("Social"),
    PRODUCTIVITY("Productivity"),
    ENTERTAINMENT("Entertainment"),
    OTHER("Other"),
}

/**
 * Reads a stored column value back. Anything unrecognised — a null column on a row written before
 * categories existed, or a name from a build that had more buckets — reads as [AppCategory.OTHER]
 * rather than throwing, so a downgrade or a hand-edited row can't crash the app.
 */
fun appCategoryFrom(stored: String?): AppCategory =
    AppCategory.entries.firstOrNull { it.name == stored } ?: AppCategory.OTHER

/**
 * The platform's own guess at what an app is ([ApplicationInfo.category]), mapped to our buckets, or
 * `null` when it doesn't tell us anything useful.
 *
 * **Deliberately conservative.** The field is declared by the app's own developer and is frequently
 * absent, so this only maps the categories that land unambiguously in one of our three buckets.
 * `CATEGORY_NEWS` and `CATEGORY_MAPS` are left unmapped on purpose: forcing a news reader into
 * "Entertainment" or "Productivity" would be the app asserting something it doesn't know, which is
 * the whole failure mode the Stats blanking rule exists to prevent. Unmapped apps show as
 * [AppCategory.OTHER] until the user picks, which is one tap in the App Picker.
 *
 * Pure and top-level so the entire map is unit-tested without a `PackageManager`. Every constant
 * referenced here exists at API 26; `CATEGORY_ACCESSIBILITY` (API 33) is deliberately not named.
 */
fun categoryFromPlatform(platformCategory: Int): AppCategory? = when (platformCategory) {
    ApplicationInfo.CATEGORY_SOCIAL -> AppCategory.SOCIAL
    ApplicationInfo.CATEGORY_PRODUCTIVITY -> AppCategory.PRODUCTIVITY
    ApplicationInfo.CATEGORY_GAME,
    ApplicationInfo.CATEGORY_AUDIO,
    ApplicationInfo.CATEGORY_VIDEO,
    ApplicationInfo.CATEGORY_IMAGE,
    -> AppCategory.ENTERTAINMENT
    else -> null
}
