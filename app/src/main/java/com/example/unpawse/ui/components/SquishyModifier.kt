package com.example.unpawse.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Scales the element down slightly while pressed to give the "squishy", tactile feel described in
 * DESIGN.md. Pass the same [InteractionSource] you give to `clickable`/`Button` so the press state
 * is shared.
 */
fun Modifier.squishy(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.98f,
): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        label = "squishyScale",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
