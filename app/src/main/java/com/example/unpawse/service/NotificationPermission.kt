package com.example.unpawse.service

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/** Whether the app may post notifications, and a way to ask for that. */
@Stable
class NotificationPermissionState(
    val granted: Boolean,
    val request: () -> Unit,
    /** Re-reads the current state; call on resume, since the user can change it in system Settings. */
    val refresh: () -> Unit,
)

/**
 * Compose wrapper over the `POST_NOTIFICATIONS` runtime permission, mirroring
 * [com.example.unpawse.ui.camera.rememberCameraPermissionState] — no Accompanist.
 *
 * The permission is declared in the manifest but was never requested at runtime, so on API 33+ the
 * app has been posting into a void since install. Below API 33 the permission does not exist, and
 * [NotificationPermissionState.request] falls back to opening system settings — which is also where
 * an API 33+ user has to go once they have denied twice and the system stops showing the dialog.
 */
@Composable
fun rememberNotificationPermissionState(): NotificationPermissionState {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(Notifications.canPost(context)) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted = Notifications.canPost(context) }

    return remember(granted) {
        NotificationPermissionState(
            granted = granted,
            request = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !granted) {
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    // Nothing left to ask for: either the platform has no dialog, or notifications
                    // were switched off wholesale, which only system settings can undo.
                    context.startActivity(Notifications.settingsIntent(context))
                }
            },
            refresh = { granted = Notifications.canPost(context) },
        )
    }
}
