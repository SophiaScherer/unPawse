package com.example.unpawse.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Periodic backstop that re-arms monitoring. The service is meant to be permanent (`START_STICKY` +
 * restarted on boot), but neither guarantee is absolute: some OEM battery managers ignore
 * `START_STICKY`, and an aggressive one can kill or rate-limit [BootReceiver]. This worker is the
 * self-heal — worst case, monitoring is back within one period instead of dark until the user next
 * opens the app.
 *
 * Like [BootReceiver] it carries no logic of its own: it calls the one gate,
 * [UsageMonitorController.startIfPermitted], which no-ops if already running, refuses without usage
 * access, and survives a disallowed background start.
 */
class MonitorHealthWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        UsageMonitorController.startIfPermitted(applicationContext)
        // Always success: "not started" is a legitimate outcome (no usage access yet), not a failure
        // to retry — the next period tries again anyway once the state changes.
        return Result.success()
    }

    companion object {
        /** Unique name so repeated schedules collapse into one chain rather than stacking. */
        private const val WORK_NAME = "usage_monitor_health"

        /**
         * Enqueues the periodic check. Idempotent via [ExistingPeriodicWorkPolicy.KEEP]: calling it
         * on every process start (from [com.example.unpawse.UnPawseApplication]) neither duplicates
         * the work nor resets its schedule. WorkManager persists the request across reboots, so this
         * complements [BootReceiver] rather than repeating it.
         *
         * 15 minutes is WorkManager's minimum period; a screen-time monitor doesn't need tighter.
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<MonitorHealthWorker>(15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
