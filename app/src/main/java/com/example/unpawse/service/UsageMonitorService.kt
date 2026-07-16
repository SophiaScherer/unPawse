package com.example.unpawse.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.example.unpawse.MainActivity
import com.example.unpawse.R
import com.example.unpawse.appContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service that drives [UsageTracker] for as long as monitoring is on. It has to be a
 * foreground service because the tracking must survive the app being backgrounded — which is
 * precisely when the user is in the app we're supposed to be watching.
 *
 * Start/stop through [UsageMonitorController] rather than directly; it enforces the usage-access
 * precondition.
 */
class UsageMonitorService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var trackerJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())

        // Guard against re-delivery / repeated start intents spawning duplicate trackers.
        if (trackerJob?.isActive != true) {
            val tracker = appContainer().usageTracker
            trackerJob = scope.launch {
                runCatching { tracker.run() }
                    .onFailure { Log.w(TAG, "Usage tracking stopped", it) }
            }
        }

        // Restart if killed: a screen-time blocker that silently stops is worse than useless.
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Screen time monitoring",
                // Low: this notification is a platform requirement, not something to interrupt with.
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Shows while unPawse is watching your app limits."
                setShowBadge(false)
            },
        )

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("unPawse is watching your limits")
            .setContentText("Tap to open unPawse")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "UsageMonitorService"
        private const val CHANNEL_ID = "usage_monitoring"
        private const val NOTIFICATION_ID = 1
    }
}

/**
 * Starts/stops [UsageMonitorService], refusing to start without usage access (the service would run
 * blind, burning a notification slot while `queryEvents` returned nothing).
 */
object UsageMonitorController {

    /** Starts monitoring if permitted. Returns whether it started. Safe to call repeatedly. */
    fun startIfPermitted(context: Context): Boolean {
        if (!UsageAccess.isGranted(context)) return false
        context.startForegroundService(Intent(context, UsageMonitorService::class.java))
        return true
    }

    fun stop(context: Context) {
        context.stopService(Intent(context, UsageMonitorService::class.java))
    }
}
