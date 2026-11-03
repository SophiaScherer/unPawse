package com.example.unpawse.service

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * The `CAMERA` runtime permission. It carries a [settingsIntent] like the special permissions do
 * because a permanent denial silences the dialog for good, and the block overlay deep-links here —
 * without a way out, a denied camera leaves the user unable to buy their app back.
 */
object CameraAccess {

    fun isGranted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    /** unPawse's own app-info page, where the camera toggle lives once the dialog has stopped coming. */
    fun settingsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
