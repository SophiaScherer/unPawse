package com.example.unpawse.ui.camera

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Creates and remembers a [LifecycleCameraController] driven by UI state. The controller bundles the
 * Preview + ImageCapture use cases, so flash and lens facing are one property each — the entire
 * CameraX surface the app needs. Reacting to [lensFacing]/[flashOn] here keeps the composable the
 * single owner of camera configuration.
 */
@Composable
fun rememberCameraController(
    lensFacing: LensFacing,
    flashOn: Boolean,
): LifecycleCameraController {
    val context = LocalContext.current
    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
        }
    }
    LaunchedEffect(lensFacing) {
        controller.cameraSelector = when (lensFacing) {
            LensFacing.BACK -> CameraSelector.DEFAULT_BACK_CAMERA
            LensFacing.FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
        }
    }
    LaunchedEffect(flashOn) {
        controller.imageCaptureFlashMode =
            if (flashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
    }
    return controller
}

/**
 * Live viewfinder. Hosts a classic [PreviewView] via [AndroidView] and binds [controller] to the
 * composition's lifecycle. Fills whatever [modifier] gives it — in [CameraScreen] that's the full
 * box the placeholder gradient used to occupy, with the controls drawn on top.
 */
@Composable
fun CameraPreview(
    controller: LifecycleCameraController,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    AndroidView(
        modifier = modifier,
        factory = { context ->
            PreviewView(context).apply {
                this.controller = controller
                controller.bindToLifecycle(lifecycleOwner)
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        },
    )
}
