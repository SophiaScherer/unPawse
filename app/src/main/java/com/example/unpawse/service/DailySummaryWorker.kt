package com.example.unpawse.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.unpawse.appContainer
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Posts the end-of-day recap. Mirrors [CaptureRetentionWorker]'s WorkManager pattern, with one
 * deliberate difference: it is scheduled and cancelled by the Settings toggle (via `AppContainer`)
 * rather than unconditionally at process start, so switching it off actually stops it.
 */
class DailySummaryWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = applicationContext.appContainer()

        // Re-check rather than trusting the schedule: an already-enqueued run can outlive the
        // cancel by a moment, and a recap nobody asked for is worse than a late one.
        if (!container.settingsRepository.dailySummaryEnabled.first()) return Result.success()

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)

        val labels = container.usageRepository.monitoredApps().associate { it.packageName to it.appLabel }
        val apps = container.usageRepository.observeTodayUsage().first().map { usage ->
            AppUsageSummary(
                label = labels[usage.packageName] ?: usage.packageName,
                minutes = usage.usedMinutes,
            )
        }

        val dayStart = startOfDayMillis(today, zone)
        val captureCount = container.captureRepository.observeCaptures().first()
            .count { it.capturedAt >= dayStart }

        Notifications.post(
            context = applicationContext,
            channelId = Notifications.CHANNEL_SUMMARY,
            id = Notifications.ID_SUMMARY,
            title = DAILY_SUMMARY_TITLE,
            text = buildDailySummary(apps, captureCount),
        )
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "daily_summary"

        /** Late enough to cover the day, early enough not to be the last thing before sleep. */
        private const val SUMMARY_HOUR = 21

        /**
         * Enqueues the daily recap, first firing at the next [SUMMARY_HOUR]. Idempotent via
         * [ExistingPeriodicWorkPolicy.KEEP], so the container calling this on every process start
         * neither duplicates the work nor pushes its schedule back a day each time.
         */
        fun schedule(context: Context, nowMillis: Long = System.currentTimeMillis()) {
            val delay = millisUntilNextHour(nowMillis, SUMMARY_HOUR, ZoneId.systemDefault())
            val request = PeriodicWorkRequestBuilder<DailySummaryWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
