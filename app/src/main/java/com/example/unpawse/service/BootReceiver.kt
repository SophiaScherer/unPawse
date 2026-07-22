package com.example.unpawse.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Restarts monitoring after a reboot. Without this the app stays dark until the user next opens it —
 * a screen-time blocker that silently stops enforcing is worse than none, because the user believes
 * they're covered.
 *
 * Deliberately holds no start/stop logic of its own: [UsageMonitorController.startIfPermitted]
 * already refuses without usage access and survives a start the platform disallows, so this is only
 * the trigger.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // A receiver exported to the system can be sent other intents; only boot is our business.
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val started = UsageMonitorController.startIfPermitted(context)
        Log.i(TAG, "Boot completed: monitoring started=$started")
    }

    private companion object {
        const val TAG = "BootReceiver"
    }
}
