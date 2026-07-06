package com.example.unpawse.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.unpawse.ui.theme.CardShape
import com.example.unpawse.ui.theme.Dimens

/**
 * The primary content container from DESIGN.md: 24dp corners, white surface, soft ambient shadow.
 * When [onClick] is supplied the whole card becomes clickable with the squishy press animation.
 *
 * [contentPadding] defaults to the 24dp "premium" card padding; pass `0.dp` when the card needs to
 * bleed content to its edges (e.g. an image or a chart that draws its own insets).
 */
@Composable
fun PawCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: RoundedCornerShape = CardShape,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
    contentPadding: Dp = Dimens.CardPadding,
    shadowElevation: Dp = 2.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val baseModifier = modifier
        .graphicsLayer {
            val scale = if (onClick != null && pressed) 0.98f else 1f
            scaleX = scale
            scaleY = scale
        }
        .shadow(elevation = shadowElevation, shape = shape, clip = false)

    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = baseModifier,
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = containerColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            interactionSource = interactionSource,
        ) {
            androidx.compose.foundation.layout.Column(Modifier.padding(contentPadding), content = content)
        }
    } else {
        Card(
            modifier = baseModifier,
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = containerColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            androidx.compose.foundation.layout.Column(Modifier.padding(contentPadding), content = content)
        }
    }
}
