package com.example.unpawse.data.export

import org.json.JSONArray
import org.json.JSONObject

/**
 * Bump when the shape below changes incompatibly, so an importer can tell the versions apart.
 *
 * v2 added `schedules` and the `weekendLimitMinutes` field on each monitored app.
 * v3 added `category` on each monitored app.
 * v4 added `blockedCount` on each usage day.
 */
const val EXPORT_FORMAT_VERSION = 4

/**
 * Everything unPawse holds about you, in one plain structure.
 *
 * Captures carry **metadata only** — no image bytes and, deliberately, no file paths. A path names
 * a location inside the app's private storage; it tells the reader nothing useful and leaks the
 * internal layout into a file the user may hand to someone else.
 */
data class ExportSnapshot(
    val exportedAtMillis: Long,
    val appVersion: String,
    val settings: ExportSettings,
    val monitoredApps: List<ExportMonitoredApp>,
    val schedules: List<ExportScheduleWindow>,
    val usage: List<ExportUsageDay>,
    val captures: List<ExportCapture>,
)

data class ExportSettings(
    val userName: String,
    val themeMode: String,
    val sensitivity: Float,
    val minConfidence: Float,
    val earnedMinutesPerCat: Int,
    val retentionDays: Int,
    val dailySummaryEnabled: Boolean,
)

data class ExportMonitoredApp(
    val packageName: String,
    val appLabel: String,
    val dailyLimitMinutes: Int,
    val enabled: Boolean,
    /** Null means weekends follow [dailyLimitMinutes]; a negative value means uncapped. */
    val weekendLimitMinutes: Int? = null,
    /** Which Stats bucket this app's time counts toward, as an `AppCategory` name. */
    val category: String,
)

/**
 * One recurring blocking window. Times are minutes since local midnight and [daysMask] is the ISO
 * day bitmask (Monday = bit 0) — the same units the app stores, since translating them here would
 * only lose the overnight-wrap meaning that `endMinuteOfDay <= startMinuteOfDay` carries.
 */
data class ExportScheduleWindow(
    val id: Long,
    val label: String,
    val packageName: String?,
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
    val daysMask: Int,
    val enabled: Boolean,
)

data class ExportUsageDay(
    val date: String,
    val packageName: String,
    val usedSeconds: Long,
    val earnedSeconds: Long,
    /** Blocks raised over this app on this day — one per breach. */
    val blockedCount: Int = 0,
)

data class ExportCapture(
    val id: String,
    val capturedAtMillis: Long,
    val confidence: Float,
    val isBonus: Boolean,
    val isFavorite: Boolean,
)

/**
 * Serializes [snapshot] to indented JSON.
 *
 * Uses `org.json` (shipped with the platform) rather than kotlinx-serialization, which would add a
 * second compiler plugin — a version-matching risk this project deliberately avoids. Pure, so the
 * shape and the no-paths guarantee are unit-tested.
 */
fun buildExportJson(snapshot: ExportSnapshot): String {
    val root = JSONObject()
        .put("formatVersion", EXPORT_FORMAT_VERSION)
        .put("exportedAt", snapshot.exportedAtMillis)
        .put("appVersion", snapshot.appVersion)
        .put("settings", snapshot.settings.toJson())
        .put("monitoredApps", snapshot.monitoredApps.toJsonArray(ExportMonitoredApp::toJson))
        .put("schedules", snapshot.schedules.toJsonArray(ExportScheduleWindow::toJson))
        .put("usage", snapshot.usage.toJsonArray(ExportUsageDay::toJson))
        .put("captures", snapshot.captures.toJsonArray(ExportCapture::toJson))

    return root.toString(INDENT_SPACES)
}

private const val INDENT_SPACES = 2

private fun <T> List<T>.toJsonArray(toJson: (T) -> JSONObject): JSONArray =
    JSONArray().also { array -> forEach { array.put(toJson(it)) } }

private fun ExportSettings.toJson() = JSONObject()
    .put("userName", userName)
    .put("themeMode", themeMode)
    .put("sensitivity", sensitivity.toDouble())
    .put("minConfidence", minConfidence.toDouble())
    .put("earnedMinutesPerCat", earnedMinutesPerCat)
    .put("retentionDays", retentionDays)
    .put("dailySummaryEnabled", dailySummaryEnabled)

private fun ExportMonitoredApp.toJson() = JSONObject()
    .put("packageName", packageName)
    .put("appLabel", appLabel)
    .put("dailyLimitMinutes", dailyLimitMinutes)
    .put("enabled", enabled)
    // JSONObject.put(String, Any?) removes the key on null, which is what we want: an absent field
    // reads as "no override" rather than as a null the reader has to interpret.
    .put("weekendLimitMinutes", weekendLimitMinutes)
    .put("category", category)

private fun ExportScheduleWindow.toJson() = JSONObject()
    .put("id", id)
    .put("label", label)
    // Likewise absent for a global window, matching how the column is stored.
    .put("packageName", packageName)
    .put("startMinuteOfDay", startMinuteOfDay)
    .put("endMinuteOfDay", endMinuteOfDay)
    .put("daysMask", daysMask)
    .put("enabled", enabled)

private fun ExportUsageDay.toJson() = JSONObject()
    .put("date", date)
    .put("packageName", packageName)
    .put("usedSeconds", usedSeconds)
    .put("earnedSeconds", earnedSeconds)
    .put("blockedCount", blockedCount)

private fun ExportCapture.toJson() = JSONObject()
    .put("id", id)
    .put("capturedAt", capturedAtMillis)
    .put("confidence", confidence.toDouble())
    .put("isBonus", isBonus)
    .put("isFavorite", isFavorite)
