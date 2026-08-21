package com.example.unpawse.data.export

import com.example.unpawse.data.capture.CaptureRetention
import com.example.unpawse.data.settings.SettingsRepository
import com.example.unpawse.data.usage.AppCategory
import org.json.JSONArray
import org.json.JSONObject

/**
 * Bump when the shape below changes incompatibly, so an importer can tell the versions apart.
 *
 * v2 added `schedules` and the `weekendLimitMinutes` field on each monitored app.
 * v3 added `category` on each monitored app.
 * v4 added `blockedCount` on each usage day.
 * v5 added the top-level `unlocks` array.
 * v6 moved the document into a ZIP bundle (`export.json` + `photos/`) and added `captures[].fileName`
 *   and `earnedMinutes`, plus `settings.warningMinutes` / `reminderMinutes`. Older documents stay
 *   readable: [parseExportJson] takes v1–v6 and lets each missing field fall back to its default.
 * v7 added `captures[].widthPx` / `heightPx`, the shape a Gallery tile is drawn at. Absent in older
 *   documents, which read as 0 — "unknown shape" — and fall back to a default ratio.
 */
const val EXPORT_FORMAT_VERSION = 7

/**
 * Everything unPawse holds about you, in one plain structure.
 *
 * Captures carry no file paths, deliberately: a path names a location inside the app's private
 * storage, tells the reader nothing useful, and leaks the internal layout into a file the user may
 * hand to someone else. From v6 the image bytes travel alongside in the bundle's `photos/` folder,
 * named by [ExportCapture.fileName].
 */
data class ExportSnapshot(
    val exportedAtMillis: Long,
    val appVersion: String,
    val settings: ExportSettings,
    val monitoredApps: List<ExportMonitoredApp>,
    val schedules: List<ExportScheduleWindow>,
    val usage: List<ExportUsageDay>,
    val unlocks: List<ExportUnlockDay>,
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
    val warningMinutes: Int = SettingsRepository.DEFAULT_WARNING_MINUTES,
    val reminderMinutes: Int = SettingsRepository.DEFAULT_REMINDER_MINUTES,
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

/**
 * Device unlocks for one day. Device-wide rather than per-app, hence its own array rather than a
 * field on [ExportUsageDay] — and counted only while the monitor service was running.
 */
data class ExportUnlockDay(
    val date: String,
    val unlockCount: Int,
)

data class ExportCapture(
    val id: String,
    val capturedAtMillis: Long,
    val confidence: Float,
    val isBonus: Boolean,
    val isFavorite: Boolean,
    /**
     * The JPEG's name inside the bundle's `photos/` folder — a bare name, never a path, so the
     * no-paths guarantee above still holds. Null in a v5 or older document, which carried no photos;
     * such a capture cannot be restored and is skipped on import.
     */
    val fileName: String? = null,
    /** What this capture bought back. Real history the Home feed reports, so it round-trips. */
    val earnedMinutes: Int = 0,
    /** The photo's own dimensions, 0 when the document predates v7. See [ExportCapture.fileName]. */
    val widthPx: Int = 0,
    val heightPx: Int = 0,
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
        .put("unlocks", snapshot.unlocks.toJsonArray(ExportUnlockDay::toJson))
        .put("captures", snapshot.captures.toJsonArray(ExportCapture::toJson))

    return root.toString(INDENT_SPACES)
}

/**
 * Reads a document back, or null if it isn't one we can honour: unparseable, no `formatVersion`, or
 * a version from a newer build whose meaning we'd be guessing at.
 *
 * There is no per-version branching. Every field is read through `opt*` falling back to its DTO
 * default, which is exactly what a field a v1–v5 document never had should mean. Individual
 * malformed elements are skipped rather than failing the whole document — a partially corrupt export
 * should restore what it can.
 */
fun parseExportJson(json: String): ExportSnapshot? {
    val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
    val version = root.optInt("formatVersion", 0)
    if (version <= 0 || version > EXPORT_FORMAT_VERSION) return null

    val settings = root.optJSONObject("settings") ?: return null
    return ExportSnapshot(
        exportedAtMillis = root.optLong("exportedAt"),
        appVersion = root.optString("appVersion"),
        settings = settings.toExportSettings(),
        monitoredApps = root.mapArray("monitoredApps", JSONObject::toMonitoredApp),
        schedules = root.mapArray("schedules", JSONObject::toScheduleWindow),
        usage = root.mapArray("usage", JSONObject::toUsageDay),
        unlocks = root.mapArray("unlocks", JSONObject::toUnlockDay),
        captures = root.mapArray("captures", JSONObject::toCapture),
    )
}

/** The format version a document declares, for reporting one we had to refuse. */
fun exportFormatVersionOf(json: String): Int? =
    runCatching { JSONObject(json).optInt("formatVersion", 0) }.getOrNull()?.takeIf { it > 0 }

private fun <T : Any> JSONObject.mapArray(key: String, parse: (JSONObject) -> T?): List<T> {
    val array = optJSONArray(key) ?: return emptyList()
    return (0 until array.length()).mapNotNull { array.optJSONObject(it)?.let(parse) }
}

/**
 * Reads a nullable int the way [buildExportJson] writes one. **Not `optInt`** — that returns 0 for an
 * absent key, and 0 means something real here: an app blocked from its first second, rather than
 * "weekends follow the everyday limit".
 */
private fun JSONObject.optNullableInt(key: String): Int? =
    if (has(key) && !isNull(key)) optInt(key) else null

private fun JSONObject.optNullableString(key: String): String? =
    if (has(key) && !isNull(key)) optString(key) else null

private fun JSONObject.toExportSettings() = ExportSettings(
    userName = optString("userName"),
    themeMode = optString("themeMode"),
    sensitivity = optDouble("sensitivity", SettingsRepository.DEFAULT_SENSITIVITY.toDouble()).toFloat(),
    minConfidence = optDouble("minConfidence", 0.0).toFloat(),
    earnedMinutesPerCat = optInt(
        "earnedMinutesPerCat",
        SettingsRepository.DEFAULT_EARNED_MINUTES_PER_CAT,
    ),
    retentionDays = optInt("retentionDays", CaptureRetention.DEFAULT_WINDOW_DAYS),
    dailySummaryEnabled = optBoolean("dailySummaryEnabled", SettingsRepository.DEFAULT_DAILY_SUMMARY),
    warningMinutes = optInt("warningMinutes", SettingsRepository.DEFAULT_WARNING_MINUTES),
    reminderMinutes = optInt("reminderMinutes", SettingsRepository.DEFAULT_REMINDER_MINUTES),
)

private fun JSONObject.toMonitoredApp(): ExportMonitoredApp? {
    val packageName = optString("packageName").ifBlank { return null }
    return ExportMonitoredApp(
        packageName = packageName,
        appLabel = optString("appLabel"),
        dailyLimitMinutes = optInt("dailyLimitMinutes"),
        enabled = optBoolean("enabled", true),
        weekendLimitMinutes = optNullableInt("weekendLimitMinutes"),
        category = optString("category", AppCategory.OTHER.name),
    )
}

private fun JSONObject.toScheduleWindow() = ExportScheduleWindow(
    id = optLong("id"),
    label = optString("label"),
    // Absent means a global window, so it must not collapse to "".
    packageName = optNullableString("packageName"),
    startMinuteOfDay = optInt("startMinuteOfDay"),
    endMinuteOfDay = optInt("endMinuteOfDay"),
    daysMask = optInt("daysMask"),
    enabled = optBoolean("enabled", true),
)

private fun JSONObject.toUsageDay(): ExportUsageDay? {
    val date = optString("date").ifBlank { return null }
    val packageName = optString("packageName").ifBlank { return null }
    return ExportUsageDay(
        date = date,
        packageName = packageName,
        usedSeconds = optLong("usedSeconds"),
        earnedSeconds = optLong("earnedSeconds"),
        blockedCount = optInt("blockedCount"),
    )
}

private fun JSONObject.toUnlockDay(): ExportUnlockDay? {
    val date = optString("date").ifBlank { return null }
    return ExportUnlockDay(date = date, unlockCount = optInt("unlockCount"))
}

private fun JSONObject.toCapture(): ExportCapture? {
    val id = optString("id").ifBlank { return null }
    return ExportCapture(
        id = id,
        capturedAtMillis = optLong("capturedAt"),
        confidence = optDouble("confidence", 0.0).toFloat(),
        isBonus = optBoolean("isBonus"),
        isFavorite = optBoolean("isFavorite"),
        fileName = optNullableString("fileName"),
        earnedMinutes = optInt("earnedMinutes"),
        widthPx = optInt("widthPx"),
        heightPx = optInt("heightPx"),
    )
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
    .put("warningMinutes", warningMinutes)
    .put("reminderMinutes", reminderMinutes)

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

private fun ExportUnlockDay.toJson() = JSONObject()
    .put("date", date)
    .put("unlockCount", unlockCount)

private fun ExportCapture.toJson() = JSONObject()
    .put("id", id)
    .put("capturedAt", capturedAtMillis)
    .put("confidence", confidence.toDouble())
    .put("isBonus", isBonus)
    .put("isFavorite", isFavorite)
    // Absent when the JPEG couldn't be read at export time, which reads correctly as "no photo".
    .put("fileName", fileName)
    .put("earnedMinutes", earnedMinutes)
    .put("widthPx", widthPx)
    .put("heightPx", heightPx)
