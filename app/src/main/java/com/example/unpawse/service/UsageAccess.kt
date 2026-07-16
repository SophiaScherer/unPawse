package com.example.unpawse.service

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings

/**
 * The `PACKAGE_USAGE_STATS` app-op: unlike a normal runtime permission there's no request dialog —
 * the user must flip a switch in system Settings, so all we can do is check it and send them there.
 */
object UsageAccess {

    /** Whether the user has granted usage access to unPawse. */
    fun isGranted(context: Context): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Intent to the usage-access screen. Some OEMs ignore the package extra and land on the full
     * list, which is why the UI copy tells the user to find unPawse there; [FLAG_ACTIVITY_NEW_TASK]
     * keeps it launchable from a non-Activity context.
     */
    fun settingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
}
