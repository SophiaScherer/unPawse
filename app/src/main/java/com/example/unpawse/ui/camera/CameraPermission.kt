package com.example.unpawse.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/** Minimal camera-permission handle for the UI: is it [granted], and a way to [request] it. */
@Stable
class CameraPermissionState(
    val granted: Boolean,
    val request: () -> Unit,
)

/**
 * Compose wrapper over the `RequestPermission` activity-result contract — no Accompanist, keeping
 * the dependency list lean like the rest of the app. Seeds [CameraPermissionState.granted] from the
 * current grant state and flips it when the system dialog returns.
 *
 * Note: a grant made manually in system Settings (app backgrounded) isn't observed here; calling
 * [request] again re-checks. A lifecycle-resume recheck can be added if that flow becomes important.
 */
@Composable
fun rememberCameraPermissionState(): CameraPermissionState {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted -> granted = isGranted }

    return remember(granted) {
        CameraPermissionState(
            granted = granted,
            request = { launcher.launch(Manifest.permission.CAMERA) },
        )
    }
}
