package com.example.unpawse.ui.camera

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.unpawse.service.CameraAccess
import com.example.unpawse.service.canAskSystemForCamera

/** Camera-permission handle for the UI: is it [granted], can it still be asked for, and how. */
@Stable
class CameraPermissionState(
    val granted: Boolean,
    /**
     * Asks the system while it will still answer, and opens app settings once it won't. Check
     * [canAskSystem] before invoking this unprompted — a silent jump to Settings is fine from a
     * button, wrong from an on-entry effect.
     */
    val request: () -> Unit,
    /** Re-reads the current state; call on resume, since the user can grant it in system Settings. */
    val refresh: () -> Unit,
    /** False once the denial is permanent — [request] then routes to Settings instead of asking. */
    val canAskSystem: Boolean,
)

/**
 * Compose wrapper over the `RequestPermission` contract — no Accompanist, keeping the dependency
 * list lean like the rest of the app. Mirrors
 * [com.example.unpawse.service.rememberNotificationPermissionState], including the settings
 * fallback: after two denials the contract shows no dialog and returns `false` immediately, so a
 * button wired straight to `launch` is visually dead.
 */
@Composable
fun rememberCameraPermissionState(): CameraPermissionState {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(CameraAccess.isGranted(context)) }
    // Deliberately not persisted: a fresh process inheriting a permanent denial wastes one tap, then
    // self-corrects. Not worth a stored flag.
    var askedOnce by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        granted = isGranted
        askedOnce = true
    }

    val canAskSystem = canAskSystemForCamera(askedOnce, CameraAccess.shouldShowRationale(context))

    return remember(granted, canAskSystem) {
        CameraPermissionState(
            granted = granted,
            request = {
                if (!granted && canAskSystem) {
                    launcher.launch(Manifest.permission.CAMERA)
                } else {
                    // Nothing left to ask for; only the app's settings page can undo this.
                    context.startActivity(CameraAccess.settingsIntent(context))
                }
            },
            refresh = { granted = CameraAccess.isGranted(context) },
            canAskSystem = canAskSystem,
        )
    }
}
