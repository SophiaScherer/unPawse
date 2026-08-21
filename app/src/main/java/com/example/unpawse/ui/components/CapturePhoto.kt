package com.example.unpawse.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import java.io.File

/** What the card says when the JPEG a row points at is no longer on disk. */
const val MISSING_PHOTO_LABEL = "Photo file missing"

/**
 * The image slot of a capture card: the stored JPEG, the preview stand-in, or — when the file a row
 * points at has gone — a tile that says so.
 *
 * Shared rather than written per screen because the Gallery grid and the actions sheet render the
 * same slot; the copy that used to sit in both is how one of them would drift from the other.
 *
 * A row can outlive its JPEG (a partial cloud restore brings the database back without the files),
 * and Coil draws *nothing* for a file it can't read — so the loss used to render as a flat coloured
 * rectangle. Naming it is the point: an invisible failure reads as the app being fine.
 *
 * [compact] drops the caption for slots too small to hold it (the sheet's 56dp thumbnail).
 */
@Composable
fun CapturePhoto(
    imagePath: String?,
    seed: Int,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    // Keyed on the path so a recycled grid slot re-tries the new photo rather than inheriting the
    // previous one's failure.
    var failed by remember(imagePath) { mutableStateOf(false) }

    when {
        imagePath == null -> CatPhotoPlaceholder(seed = seed, modifier = modifier)

        failed -> MissingPhoto(modifier = modifier, compact = compact)

        else -> AsyncImage(
            model = File(imagePath),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            // Coil answers "is the file readable?" as part of loading it; File.exists() here would
            // be disk IO on the main thread for a question already being asked.
            onError = { failed = true },
            modifier = modifier,
        )
    }
}

@Composable
private fun MissingPhoto(modifier: Modifier, compact: Boolean) {
    Column(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.BrokenImage,
            // Not decorative: this icon is the whole message in the compact slot.
            contentDescription = MISSING_PHOTO_LABEL,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(if (compact) 20.dp else 28.dp),
        )
        if (!compact) {
            Text(
                text = MISSING_PHOTO_LABEL,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp, start = 8.dp, end = 8.dp),
            )
        }
    }
}
