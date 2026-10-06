package com.example.unpawse.ui.camera

import android.Manifest
import androidx.compose.runtime.Composable
import com.example.unpawse.service.CameraAccess
import com.example.unpawse.service.RuntimePermissionState
import com.example.unpawse.service.rememberRuntimePermissionState

/** Camera-permission handle for the UI: is it granted, can it still be asked for, and how. */
typealias CameraPermissionState = RuntimePermissionState

/**
 * The `CAMERA` runtime permission, falling back to the app's settings page once Android stops
 * showing the dialog — the block overlay deep-links here, so a dead button would leave the user no
 * way to buy their app back.
 */
@Composable
fun rememberCameraPermissionState(): CameraPermissionState =
    rememberRuntimePermissionState(
        permission = Manifest.permission.CAMERA,
        isGranted = CameraAccess::isGranted,
        settingsIntent = CameraAccess::settingsIntent,
    )
