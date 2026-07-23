package com.example.unpawse.ui.gallery

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.unpawse.ui.components.CatPhotoPlaceholder
import java.io.File

/**
 * Bottom sheet of actions for a single capture: favorite/unfavorite, share, and delete (guarded by a
 * confirm dialog). Header shows a thumbnail + time for context. Stateless beyond the local
 * confirm-dialog toggle; the caller owns which capture is selected and what each action does.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureActionsSheet(
    capture: CaptureItem,
    onDismiss: () -> Unit,
    onToggleFavorite: (CaptureItem) -> Unit,
    onShare: (CaptureItem) -> Unit,
    onDelete: (CaptureItem) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    var confirmDelete by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            SheetHeader(capture)
            Spacer(Modifier.size(8.dp))

            ActionRow(
                icon = if (capture.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                label = if (capture.isFavorite) "Remove from favorites" else "Add to favorites",
                tint = MaterialTheme.colorScheme.primary,
                onClick = { onToggleFavorite(capture) },
            )
            ActionRow(
                icon = Icons.Filled.Share,
                label = "Share",
                tint = MaterialTheme.colorScheme.onSurface,
                onClick = { onShare(capture) },
            )
            ActionRow(
                icon = Icons.Filled.Delete,
                label = "Delete",
                tint = MaterialTheme.colorScheme.error,
                onClick = { confirmDelete = true },
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete photo?") },
            text = { Text("This permanently removes the photo. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete(capture)
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SheetHeader(capture: CaptureItem) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val thumbModifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp))
        if (capture.imagePath != null) {
            AsyncImage(
                model = File(capture.imagePath),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = thumbModifier,
            )
        } else {
            CatPhotoPlaceholder(seed = capture.id.hashCode(), modifier = thumbModifier)
        }
        Spacer(Modifier.size(12.dp))
        Column {
            Text(capture.timeLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                capture.caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ActionRow(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.size(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = tint)
    }
}
