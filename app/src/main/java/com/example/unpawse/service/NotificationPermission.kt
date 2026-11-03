package com.example.unpawse.service

import android.Manifest
import android.os.Build
import androidx.compose.runtime.Composable

/** Whether the app may post notifications, and a way to ask for that. */
typealias NotificationPermissionState = RuntimePermissionState

/**
 * The `POST_NOTIFICATIONS` runtime permission. Below API 33 it doesn't exist and every request opens
 * system settings; on API 33+ the dialog is used until Android stops showing it, then settings too.
 * Settings is also the answer when the permission is granted but the app's notifications are
 * switched off, which [Notifications.canPost] counts as not granted.
 */
@Composable
fun rememberNotificationPermissionState(): NotificationPermissionState =
    rememberRuntimePermissionState(
        permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.POST_NOTIFICATIONS
        } else {
            null
        },
        isGranted = Notifications::canPost,
        settingsIntent = Notifications::settingsIntent,
    )
