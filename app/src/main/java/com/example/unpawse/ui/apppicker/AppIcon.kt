package com.example.unpawse.ui.apppicker

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Rasterised size for launcher icons — comfortably above the 40dp tile at high densities. */
private const val ICON_PX = 96

/**
 * Loads one app's launcher icon off the main thread, for **visible rows only**. Icons are
 * `Drawable`s and there can be hundreds of installed apps, so decoding them all up-front (and
 * parking them in UI state) would waste memory and jank the list; `produceState` keyed on the
 * package scopes the work to what's on screen.
 *
 * Returns `null` while loading or if the package can't be resolved (e.g. uninstalled mid-scroll).
 */
@Composable
private fun rememberAppIcon(packageName: String): ImageBitmap? {
    val context = LocalContext.current
    return produceState<ImageBitmap?>(initialValue = null, packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager
                    .getApplicationIcon(packageName)
                    .toBitmap(width = ICON_PX, height = ICON_PX)
                    .asImageBitmap()
            }.getOrNull()
        }
    }.value
}

/**
 * The picker's leading tile: the real launcher icon once loaded, falling back to the app's initial
 * on the standard tinted tile so the row never collapses or flickers empty.
 */
@Composable
fun AppIcon(
    packageName: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    val icon = rememberAppIcon(packageName)

    Box(
        modifier = modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
            )
        } else {
            Text(
                text = label.take(1).uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
