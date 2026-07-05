package com.example.unpawse.ui.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.unpawse.ui.components.squishy

/**
 * UI state for the camera capture screen. This is a static placeholder — no CameraX / permissions
 * in this pass. When the real viewfinder lands, the gradient background becomes the camera preview
 * and [flashOn] / detection state get wired up.
 */
data class CameraUiState(
    val hintText: String = "Find your cat to continue.",
    val flashOn: Boolean = false,
) {
    companion object {
        fun sample() = CameraUiState()
    }
}

@Composable
fun CameraScreen(
    state: CameraUiState,
    modifier: Modifier = Modifier,
    onClose: () -> Unit = {},
    onToggleFlash: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenGallery: () -> Unit = {},
    onCapture: () -> Unit = {},
    onFlipCamera: () -> Unit = {},
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                // Warm, softly-blurred "viewfinder" stand-in for the camera feed.
                Brush.verticalGradient(
                    listOf(Color(0xFFD8C4B0), Color(0xFFB89B84), Color(0xFF8C7360)),
                ),
            ),
    ) {
        // Top controls.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TranslucentCircleButton(Icons.Filled.Close, "Close", onClose)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TranslucentCircleButton(Icons.Filled.FlashOff, "Flash", onToggleFlash)
                TranslucentCircleButton(Icons.Filled.Settings, "Settings", onOpenSettings)
            }
        }

        // Hint pill.
        Text(
            text = state.hintText,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 72.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.4f))
                .padding(horizontal = 20.dp, vertical = 10.dp),
        )

        // Guide frame.
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.7f)
                .aspectRatio(0.85f)
                .border(1.5.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(28.dp)),
        )

        // Bottom controls.
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TranslucentCircleButton(Icons.Outlined.GridView, "Gallery", onOpenGallery)
            ShutterButton(onCapture)
            TranslucentCircleButton(Icons.Filled.FlipCameraAndroid, "Flip camera", onFlipCamera)
        }
    }
}

@Composable
private fun TranslucentCircleButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.3f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = Color.White, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun ShutterButton(onCapture: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(76.dp)
            .squishy(interactionSource, pressedScale = 0.92f)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.3f))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onCapture)
            .padding(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

@Preview
@Composable
private fun CameraScreenPreview() {
    CameraScreen(state = CameraUiState.sample())
}
