package com.example.unpawse.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.unpawse.appContainer
import com.example.unpawse.data.capture.CaptureRetention
import java.util.concurrent.TimeUnit

/**
 * Prunes the capture library to the rolling retention window ([CaptureRetention.WINDOW_DAYS]):
 * non-favorite photos older than the cutoff are deleted (row + JPEG); favorites are kept forever.
 * Mirrors [MonitorHealthWorker]'s WorkManager pattern — persisted across reboots and idempotently
 * scheduled once per process start.
 */
class CaptureRetentionWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val cutoff = CaptureRetention.cutoff(System.currentTimeMillis())
        applicationContext.appContainer().captureRepository.purgeExpired(cutoff)
        return Result.success()
    }

    companion object {
        /** Unique name so repeated schedules collapse into one chain rather than stacking. */
        private const val WORK_NAME = "capture_retention"

        /**
         * Enqueues the daily purge. Idempotent via [ExistingPeriodicWorkPolicy.KEEP]: calling it on
         * every process start (from [com.example.unpawse.UnPawseApplication]) neither duplicates the
         * work nor resets its schedule. A day's granularity is ample — the Gallery already hides
         * anything past the cutoff on read, so the worker only needs to reclaim storage eventually.
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<CaptureRetentionWorker>(1, TimeUnit.DAYS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
