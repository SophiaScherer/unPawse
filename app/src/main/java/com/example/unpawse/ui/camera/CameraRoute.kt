package com.example.unpawse.ui.camera

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Stateful wrapper around [CameraScreen]. Owns the [CameraViewModel], the camera permission, and the
 * CameraX controller, and reacts to one-shot [CameraEvent]s. Keeps [CameraScreen] itself pure and
 * preview-able; this is the seam the NavHost wires in place of `SampleData.cameraState`.
 */
@Composable
fun CameraRoute(
    onClose: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel: CameraViewModel = viewModel(factory = CameraViewModel.factory(context))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val permission = rememberCameraPermissionState()

    // Prompt on first entry if we don't already have access.
    LaunchedEffect(Unit) {
        if (!permission.granted) permission.request()
    }

    // Capture outcomes surface through the hint text in [state], and we deliberately keep the user
    // on the camera after a save so they can keep snapping (the Gallery button is right there).
    // Draining the event stream here leaves room to add transient feedback (haptics/snackbar) later
    // without touching the ViewModel.
    LaunchedEffect(Unit) {
        viewModel.events.collect { /* stay on the camera; hint text already reflects the outcome */ }
    }

    if (permission.granted) {
        val controller = rememberCameraController(state.lensFacing, state.flashOn)
        CameraScreen(
            state = state,
            modifier = modifier,
            background = { CameraPreview(controller, modifier = Modifier.fillMaxSize()) },
            onClose = onClose,
            onToggleFlash = viewModel::onToggleFlash,
            onOpenSettings = onOpenSettings,
            onOpenGallery = onOpenGallery,
            onFlipCamera = viewModel::onFlipCamera,
            onCapture = { viewModel.onShutter { controller.captureImage(context) } },
        )
    } else {
        CameraPermissionPrompt(
            onGrant = permission.request,
            onClose = onClose,
            modifier = modifier,
        )
    }
}

/** Shown when camera access hasn't been granted; offers to re-request or back out. */
@Composable
private fun CameraPermissionPrompt(
    onGrant: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                Icons.Filled.PhotoCamera,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Text(
                "Camera access needed",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Text(
                "unPawse needs your camera to snap and verify your cat.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onGrant) { Text("Allow camera") }
            TextButton(onClick = onClose) { Text("Not now") }
        }
    }
}
