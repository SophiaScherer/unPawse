package com.example.unpawse.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.absoluteValue

/**
 * Stand-in for a captured cat photo. We do NOT ship the mockup's stock photos, so every image slot
 * renders a deterministic warm gradient (chosen by [seed]) with a faint paw glyph. Swap this for a
 * real image loader (Coil `AsyncImage`) once photos exist — call sites only pass a seed + modifier.
 */
@Composable
fun CatPhotoPlaceholder(
    seed: Int,
    modifier: Modifier = Modifier,
) {
    val gradient = warmGradients[seed.absoluteValue % warmGradients.size]
    Box(
        modifier = modifier.background(Brush.linearGradient(gradient)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Pets,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.35f),
            modifier = Modifier.fillMaxSize(0.35f),
        )
    }
}

/** Circular avatar stand-in showing the user's initial; replaces the mockup's profile photo. */
@Composable
fun InitialsAvatar(
    initial: Char,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 44.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial.uppercase(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

// Warm gradient pairs built from the brand's pink/coral/cream tones.
private val warmGradients: List<List<Color>> = listOf(
    listOf(Color(0xFFF5B6C8), Color(0xFFF4B59E)),
    listOf(Color(0xFFFFD9E2), Color(0xFFF5B6C8)),
    listOf(Color(0xFFFFB59E), Color(0xFF9F4122)),
    listOf(Color(0xFFEAD9C0), Color(0xFFCDA98D)),
    listOf(Color(0xFFF6EBEC), Color(0xFFD4C2C6)),
)
