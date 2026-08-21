package com.example.unpawse.data.export

import android.content.ContentResolver
import android.net.Uri
import com.example.unpawse.data.capture.Capture
import com.example.unpawse.data.capture.CaptureRepository
import com.example.unpawse.data.schedule.ScheduleRepository
import com.example.unpawse.data.schedule.ScheduleWindow
import com.example.unpawse.data.settings.SettingsRepository
import com.example.unpawse.data.unlocks.DailyUnlocks
import com.example.unpawse.data.unlocks.UnlockRepository
import com.example.unpawse.data.usage.DailyUsage
import com.example.unpawse.data.usage.MonitoredApp
import com.example.unpawse.data.usage.UsageRepository
import com.example.unpawse.ml.sensitivityToMinConfidence
import com.example.unpawse.ui.theme.themeModeFrom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Gathers everything the app stores into an [ExportSnapshot] and writes it out as JSON.
 *
 * Reads each store through its repository rather than the DAOs, so the export can never see fields
 * the rest of the app can't. Splitting the gathering ([snapshot]) from the writing ([exportTo])
 * keeps the interesting half testable without a `ContentResolver`.
 */
class ExportRepository(
    private val settings: SettingsRepository,
    private val usage: UsageRepository,
    private val unlocks: UnlockRepository,
    private val schedules: ScheduleRepository,
    private val captures: CaptureRepository,
    private val contentResolver: ContentResolver,
    private val appVersion: String,
    private val now: () -> Long = System::currentTimeMillis,
) {

    suspend fun snapshot(): ExportSnapshot = snapshot(captures.observeCaptures().first())

    /**
     * Overload taking the captures the caller already holds, so [exportTo] can pair each row with
     * its JPEG without reading the library twice.
     */
    suspend fun snapshot(captureList: List<Capture>): ExportSnapshot {
        val sensitivity = settings.sensitivity.first()
        return ExportSnapshot(
            exportedAtMillis = now(),
            appVersion = appVersion,
            settings = ExportSettings(
                userName = settings.userName.first(),
                themeMode = themeModeFrom(settings.darkModeOverride.first()).name,
                sensitivity = sensitivity,
                // The derived gate too: on its own the slider position means nothing to a reader.
                minConfidence = sensitivityToMinConfidence(sensitivity),
                earnedMinutesPerCat = settings.earnedMinutesPerCat.first(),
                retentionDays = settings.retentionDays.first(),
                dailySummaryEnabled = settings.dailySummaryEnabled.first(),
                warningMinutes = settings.warningMinutes.first(),
                reminderMinutes = settings.reminderMinutes.first(),
            ),
            monitoredApps = usage.monitoredApps().map(MonitoredApp::toExport),
            schedules = schedules.allWindows().map(ScheduleWindow::toExport),
            usage = usage.allUsage().map(DailyUsage::toExport),
            unlocks = unlocks.allUnlocks().map(DailyUnlocks::toExport),
            captures = captureList.map(Capture::toExport),
        )
    }

    /**
     * Writes the export to a document the user picked. Returns false if the stream could not be
     * opened — the picked location can disappear between choosing it and writing to it.
     */
    suspend fun exportTo(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val captureList = captures.observeCaptures().first()
        val json = buildExportJson(snapshot(captureList))
        val photos = captureList.map { capture ->
            PhotoSource(File(capture.filePath).name) { File(capture.filePath).inputStream() }
        }
        runCatching {
            contentResolver.openOutputStream(uri)?.use { writeBundle(it, json, photos) } != null
        }.getOrDefault(false)
    }

    companion object {
        /** e.g. "unpawse-export-2026-07-27.zip"; offered as the picker's default filename. */
        fun defaultFileName(todayMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
            val date = Instant.ofEpochMilli(todayMillis).atZone(zone).toLocalDate()
            return "unpawse-export-$date.zip"
        }

        fun defaultFileName(today: LocalDate): String = "unpawse-export-$today.zip"
    }
}

private fun MonitoredApp.toExport() = ExportMonitoredApp(
    packageName = packageName,
    appLabel = appLabel,
    dailyLimitMinutes = dailyLimitMinutes,
    enabled = enabled,
    weekendLimitMinutes = weekendLimitMinutes,
    category = category.name,
)

private fun ScheduleWindow.toExport() = ExportScheduleWindow(
    id = id,
    label = label,
    packageName = packageName,
    startMinuteOfDay = startMinuteOfDay,
    endMinuteOfDay = endMinuteOfDay,
    daysMask = daysMask,
    enabled = enabled,
)

private fun DailyUsage.toExport() = ExportUsageDay(
    date = date,
    packageName = packageName,
    usedSeconds = usedSeconds,
    earnedSeconds = earnedSeconds,
    blockedCount = blockedCount,
)

private fun DailyUnlocks.toExport() = ExportUnlockDay(
    date = date,
    unlockCount = unlockCount,
)

/** Note the absence of `filePath` — see [ExportCapture]. A bare file name is not a path. */
private fun Capture.toExport() = ExportCapture(
    id = id,
    capturedAtMillis = capturedAt,
    confidence = confidence,
    isBonus = isBonus,
    isFavorite = isFavorite,
    fileName = File(filePath).name,
    earnedMinutes = earnedMinutes,
    widthPx = widthPx,
    heightPx = heightPx,
)
