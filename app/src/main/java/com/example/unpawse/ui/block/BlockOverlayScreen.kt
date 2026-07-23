package com.example.unpawse.ui.block

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.unpawse.ui.components.CatPhotoPlaceholder
import com.example.unpawse.ui.theme.UnPawseTheme

/** Copy for the "Time for a Break" overlay. [showCamera] is false for a focus hard-block. */
data class BlockUiState(
    val appName: String = "this app",
    val headline: String = "Time for a Break 🐱",
    val subtitle: String = "You've reached today's limit for this app.",
    val body: String = "To continue using this app, go find your cat and take a picture.",
    val footer: String = "Healthy habits happen one break at a time.",
    val showCamera: Boolean = true,
) {
    companion object {
        fun sample() = BlockUiState()

        /** The real thing: names the app whose limit was actually hit. */
        fun forApp(appName: String) = BlockUiState(
            appName = appName,
            subtitle = "You've reached today's limit for $appName.",
            body = "To keep using $appName, go find your cat and take a picture.",
        )

        /**
         * A focus-session hard block: no camera escape (the "+15 min cat" path is hidden), the app
         * unlocks only when the timer ends. The user can still leave via "Exit App".
         */
        fun forFocus(appName: String) = BlockUiState(
            appName = appName,
            headline = "Focus mode 🎯",
            subtitle = "$appName is paused",
            body = "Stay focused — this app unlocks when your session ends.",
            footer = "You've got this.",
            showCamera = false,
        )
    }
}

/**
 * Full-screen "limit reached" takeover. In production this would be drawn over the blocked app;
 * here it is a normal nav destination (reachable from Home) so the design can be reviewed. No
 * bottom bar — the hosting scaffold hides it on this route.
 */
@Composable
fun BlockOverlayScreen(
    state: BlockUiState,
    modifier: Modifier = Modifier,
    onOpenCamera: () -> Unit = {},
    onExit: () -> Unit = {},
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceDim)
            .safeDrawingPadding()
            .padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(contentAlignment = Alignment.TopCenter) {
            CatEars()
            Surface(
                shape = RoundedCornerShape(32.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                shadowElevation = 4.dp,
                modifier = Modifier.padding(top = 24.dp),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    MeowChipRow()
                    CatIllustration()
                    Spacer(Modifier.height(20.dp))
                    Text(
                        state.headline,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        state.subtitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        state.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(24.dp))
                    if (state.showCamera) {
                        Button(
                            onClick = onOpenCamera,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        ) {
                            Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text("Open Camera", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    TextButton(onClick = onExit, modifier = Modifier.padding(top = 4.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.size(8.dp))
                        Text("Exit App", color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        state.footer,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** Two soft circles peeking above the card to read as cat ears — the brand signature. */
@Composable
private fun CatEars() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(90.dp),
        modifier = Modifier.padding(top = 4.dp),
    ) {
        repeat(2) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }
    }
}

@Composable
private fun MeowChipRow() {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Text(
            "Meow!",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun CatIllustration() {
    Box(
        modifier = Modifier
            .size(180.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        CatPhotoPlaceholder(
            seed = 2,
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape),
        )
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun BlockOverlayPreview() {
    UnPawseTheme {
        BlockOverlayScreen(state = BlockUiState.sample())
    }
}
