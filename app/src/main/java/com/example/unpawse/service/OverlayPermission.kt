package com.example.unpawse.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * The `SYSTEM_ALERT_WINDOW` ("display over other apps") permission. Like usage access this is a
 * special permission with no runtime dialog — it's a system Settings toggle. Without it we can
 * detect that a limit was hit but can't actually show the block over the offending app.
 */
object OverlayPermission {

    fun isGranted(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun settingsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.fromParts("package", context.packageName, null),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
