package com.example.unpawse.data.export

import org.json.JSONArray
import org.json.JSONObject

/** Bump when the shape below changes incompatibly, so an importer can tell the versions apart. */
const val EXPORT_FORMAT_VERSION = 1

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
)

data class ExportUsageDay(
    val date: String,
    val packageName: String,
    val usedSeconds: Long,
    val earnedSeconds: Long,
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

private fun ExportUsageDay.toJson() = JSONObject()
    .put("date", date)
    .put("packageName", packageName)
    .put("usedSeconds", usedSeconds)
    .put("earnedSeconds", earnedSeconds)

private fun ExportCapture.toJson() = JSONObject()
    .put("id", id)
    .put("capturedAt", capturedAtMillis)
    .put("confidence", confidence.toDouble())
    .put("isBonus", isBonus)
    .put("isFavorite", isFavorite)
