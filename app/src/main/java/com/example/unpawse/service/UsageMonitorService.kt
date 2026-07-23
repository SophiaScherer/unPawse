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
import com.example.unpawse.data.AppContainer
import com.example.unpawse.ui.block.BlockUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
                launch { observeBlockRequired() }
                launch { dismissBlockWhenUserLeaves() }
                launch { runFocusSessionLifecycle() }
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

    /**
     * Raises the block overlay when a monitored app must be blocked — either its daily limit was hit
     * (escapable via the camera) or a focus session is running (a hard block, no camera). The tracker
     * signals once per breach, so there's no debouncing to do here.
     */
    private suspend fun observeBlockRequired() {
        val container = appContainer()
        container.usageTracker.blockRequired.collect { event ->
            val label = container.usageRepository.appLabel(event.packageName) ?: event.packageName
            when (event.reason) {
                BlockReason.LIMIT -> {
                    // Arm the debt before showing, so the camera knows what it's earning back for.
                    container.blockSession.start(event.packageName)
                    // WindowManager.addView is main-thread only.
                    withContext(Dispatchers.Main) {
                        container.blockOverlayController.show(
                            packageName = event.packageName,
                            reason = BlockReason.LIMIT,
                            state = BlockUiState.forApp(label),
                            onOpenCamera = { onOpenCamera(container.blockOverlayController) },
                            onExit = { onExit(container) },
                        )
                    }
                }
                BlockReason.FOCUS -> {
                    // No blockSession is armed: there's nothing to earn back until the timer ends.
                    withContext(Dispatchers.Main) {
                        container.blockOverlayController.show(
                            packageName = event.packageName,
                            reason = BlockReason.FOCUS,
                            state = BlockUiState.forFocus(label),
                            onOpenCamera = {},
                            onExit = { onFocusExit(container) },
                        )
                    }
                }
            }
        }
    }

    /**
     * Ends a focus overlay when its session ends — whether the user stopped it early (`endTimeMillis`
     * goes null) or the timer elapsed. `collectLatest` cancels the pending [delay] if the session is
     * restarted or stopped, so there's at most one live timer.
     */
    private suspend fun runFocusSessionLifecycle() {
        val container = appContainer()
        container.focusSession.endTimeMillis.collectLatest { end ->
            if (end == null) {
                if (container.blockOverlayController.blockReason == BlockReason.FOCUS) {
                    withContext(Dispatchers.Main) { container.blockOverlayController.hide() }
                }
            } else {
                val remaining = end - System.currentTimeMillis()
                if (remaining > 0) delay(remaining)
                // Timer elapsed: clearing the session re-emits null above, which takes the overlay down.
                container.focusSession.stop()
            }
        }
    }

    /**
     * Takes the overlay down once the user is no longer in the blocked app.
     *
     * An overlay window outlives app switches by design, so without this a home-gesture (or any
     * other exit that isn't our "Exit App" button) would leave the block stranded on top of the
     * launcher. Returning to the app re-blocks via the tracker, so nothing is lost by hiding it.
     */
    private suspend fun dismissBlockWhenUserLeaves() {
        val container = appContainer()
        container.usageTracker.foregroundApp.collect { current ->
            val blocked = container.blockOverlayController.blockedPackage ?: return@collect
            if (current != null && current != blocked) {
                withContext(Dispatchers.Main) { container.blockOverlayController.hide() }
            }
        }
    }

    /**
     * The overlay sits above *every* app including our own, so it must come down before we bring
     * the camera up — otherwise the user would be staring at the block on top of the viewfinder.
     */
    private fun onOpenCamera(controller: BlockOverlayController) {
        controller.hide()
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_OPEN_CAMERA, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
        )
    }

    /**
     * Send the user home rather than back into the blocked app. Returning to it re-triggers the
     * block anyway (the tracker re-arms on a foreground change), so the limit still holds.
     *
     * Walking away also disarms the session: a cat photographed later, on a whim, shouldn't
     * silently pay off a block the user already abandoned.
     */
    private fun onExit(container: AppContainer) {
        container.blockOverlayController.hide()
        container.blockSession.clear()
        goHome()
    }

    /**
     * Focus "Exit App": leave the blocked app but keep the session running — returning re-blocks, so
     * the focus still holds. Deliberately does not clear the focus session or touch the block debt.
     */
    private fun onFocusExit(container: AppContainer) {
        container.blockOverlayController.hide()
        goHome()
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
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
 * Starts/stops [UsageMonitorService], refusing to start when it isn't allowed to: without usage
 * access (the service would run blind, burning a notification slot while `queryEvents` returned
 * nothing), or when the platform forbids the start outright.
 *
 * This is the single entry point every trigger routes through — the root resume effect in
 * [com.example.unpawse.UnPawseApp], process start in [com.example.unpawse.UnPawseApplication], and
 * [BootReceiver] — so both refusals are enforced here rather than re-implemented at each call site.
 * New triggers should call this and add nothing of their own.
 */
object UsageMonitorController {

    private const val TAG = "UsageMonitorController"

    /** Starts monitoring if permitted. Returns whether it started. Safe to call repeatedly. */
    fun startIfPermitted(context: Context): Boolean {
        if (!UsageAccess.isGranted(context)) return false
        return try {
            context.startForegroundService(Intent(context, UsageMonitorService::class.java))
            true
        } catch (e: IllegalStateException) {
            // Android 12+ throws ForegroundServiceStartNotAllowedException (an IllegalStateException,
            // so this needs no SDK_INT branch) when a background process starts a foreground service.
            // Reachable from every trigger that can run with no UI up — a START_STICKY revival, a
            // boot broadcast — and it would take the process down. Skipping is safe: whichever
            // trigger fires next from an allowed state starts us, and the caller sees `false`.
            Log.w(TAG, "Foreground start not allowed from the current process state", e)
            false
        }
    }

    fun stop(context: Context) {
        context.stopService(Intent(context, UsageMonitorService::class.java))
    }
}
