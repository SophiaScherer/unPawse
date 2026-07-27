package com.example.unpawse.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.unpawse.MainActivity
import com.example.unpawse.R

/**
 * The app's one notification surface: channels, the permission check, and posting.
 *
 * Everything used to be built privately inside [UsageMonitorService], which was fine while the
 * ongoing monitoring badge was the only notification. The limit warning and daily summary post from
 * elsewhere, so the channel definitions and the small-icon/deep-link boilerplate live here instead
 * of being copied per caller.
 */
object Notifications {

    /** Ongoing, silent. The platform requires it while the monitoring service runs. */
    const val CHANNEL_MONITORING = "usage_monitoring"

    /** Time-sensitive nudges — "you're about to hit your limit". */
    const val CHANNEL_ALERTS = "alerts"

    /** The end-of-day recap. Quiet by design; it is never urgent. */
    const val CHANNEL_SUMMARY = "summary"

    const val ID_MONITORING = 1
    const val ID_WARNING = 2
    const val ID_SUMMARY = 3

    /**
     * Creates every channel. Idempotent — re-creating an existing channel updates its name and
     * description but leaves the user's own importance and sound choices alone, which is why it is
     * safe to call on each service start.
     */
    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            channel(
                CHANNEL_MONITORING,
                "Screen time monitoring",
                "Shows while unPawse is watching your app limits.",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { setShowBadge(false) },
        )
        manager.createNotificationChannel(
            channel(
                CHANNEL_ALERTS,
                "Limit warnings",
                "Warns you shortly before an app hits its daily limit.",
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
        manager.createNotificationChannel(
            channel(
                CHANNEL_SUMMARY,
                "Daily summary",
                "A once-a-day recap of your screen time and cats.",
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    /**
     * Whether the app may post notifications. Below API 33 there is no runtime permission, so the
     * only thing that can block us is the user switching notifications off entirely.
     */
    fun canPost(context: Context): Boolean {
        val permissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        return permissionGranted && manager?.areNotificationsEnabled() != false
    }

    /** Builds the ongoing badge for [UsageMonitorService.startForeground]. */
    fun monitoringNotification(context: Context): Notification =
        builder(context, CHANNEL_MONITORING, "unPawse is watching your limits", "Tap to open unPawse")
            .setOngoing(true)
            .build()

    /**
     * Posts a notification, silently doing nothing when we aren't allowed to. Callers are background
     * workers and services with no UI to report a failure through, so the check belongs here rather
     * than at each call site.
     */
    fun post(context: Context, channelId: String, id: Int, title: String, text: String) {
        if (!canPost(context)) return
        ensureChannels(context)
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.notify(id, builder(context, channelId, title, text).setAutoCancel(true).build())
    }

    /** Opens the system notification settings for this app — where a denied permission is undone. */
    fun settingsIntent(context: Context): Intent =
        Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun channel(id: String, name: String, description: String, importance: Int) =
        NotificationChannel(id, name, importance).apply { this.description = description }

    private fun builder(context: Context, channelId: String, title: String, text: String) =
        Notification.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openAppIntent(context))

    private fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE,
    )
}
