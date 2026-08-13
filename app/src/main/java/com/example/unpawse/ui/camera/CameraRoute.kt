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
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.unpawse.appContainer

/**
 * Stateful wrapper around [CameraScreen]. Owns the [CameraViewModel], the camera permission, and the
 * CameraX controller, and reacts to one-shot [CameraEvent]s. Keeps [CameraScreen] itself pure and
 * preview-able; this is what the NavHost wires in.
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

    // Gated on canAskSystem: past a permanent denial `request` opens system Settings, and doing that
    // unprompted would throw the user out of the app for merely arriving here.
    LaunchedEffect(Unit) {
        if (!permission.granted && permission.canAskSystem) permission.request()
    }

    // A grant made on that settings page is never reported back to us, so re-read on return.
    LifecycleResumeEffect(Unit) {
        permission.refresh()
        onPauseOrDispose { }
    }

    // Capture outcomes surface through the hint text in [state], so we deliberately keep the user on
    // the camera after a save rather than yanking them elsewhere. The one thing worth acting on is a
    // capture that actually bought time: the crediting itself happens in the ViewModel, but the
    // overlay is a window this side owns, so take it down here. A capped or cooling-down capture
    // leaves it up on purpose — the app is still blocked, so hiding it would be a lie.
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            if (event is CameraEvent.Saved && event.outcome is RewardOutcome.Earned) {
                context.appContainer().blockOverlayController.hide()
            }
        }
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
            canAskSystem = permission.canAskSystem,
            onGrant = permission.request,
            onClose = onClose,
            modifier = modifier,
        )
    }
}

/**
 * Shown when camera access hasn't been granted; offers to re-request or back out. With
 * [canAskSystem] false the copy points at Settings rather than repeating an offer the system will
 * refuse. "Not now" stays in both branches — it's the only way back out of the overlay's deep link.
 */
@Composable
private fun CameraPermissionPrompt(
    canAskSystem: Boolean,
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
                if (canAskSystem) {
                    "unPawse needs your camera to snap and verify your cat."
                } else {
                    "Camera access is turned off for unPawse. Turn it on in Settings to " +
                        "photograph your cat."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onGrant) {
                Text(if (canAskSystem) "Allow camera" else "Open settings")
            }
            TextButton(onClick = onClose) { Text("Not now") }
        }
    }
}
