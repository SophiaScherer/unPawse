package com.example.unpawse.service

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * The `CAMERA` runtime permission. It carries a [settingsIntent] like the special permissions do
 * because two denials silence the dialog for good, and the block overlay deep-links here — without a
 * way out, a denied camera leaves the user unable to buy their app back.
 */
object CameraAccess {

    fun isGranted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Whether the platform would offer a rationale before asking again. False without an `Activity`,
     * which is the safe answer — it only ever widens [canAskSystemForCamera] when true.
     */
    fun shouldShowRationale(context: Context): Boolean {
        val activity = context.findActivity() ?: return false
        return ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
    }

    /** unPawse's own app-info page, where the camera toggle lives once the dialog has stopped coming. */
    fun settingsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

/**
 * Whether asking the system again could still produce a dialog — the platform has no such query, so
 * it is inferred. Both inputs are needed: [showRationale] is false *both* before the first ask and
 * after a permanent denial, and [askedOnce] is what tells those apart.
 */
internal fun canAskSystemForCamera(askedOnce: Boolean, showRationale: Boolean): Boolean =
    !askedOnce || showRationale

/**
 * The hosting `Activity`, or null. Null-safe rather than a cast because composables here can render
 * in the service-owned overlay window, where the context is the Application — an unconditional cast
 * crashed the app that way once already (see `UnPawseTheme`).
 */
private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
