package com.example.unpawse.ui.photos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoDelete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.unpawse.data.capture.CaptureRetention
import com.example.unpawse.ui.components.BackHeader
import com.example.unpawse.ui.components.Chevron
import com.example.unpawse.ui.components.ConfirmDialog
import com.example.unpawse.ui.components.OptionPickerDialog
import com.example.unpawse.ui.components.SectionLabel
import com.example.unpawse.ui.components.SettingsGroup
import com.example.unpawse.ui.components.SettingsRow
import com.example.unpawse.ui.components.ValueText
import com.example.unpawse.ui.theme.Dimens
import com.example.unpawse.ui.theme.UnPawseTheme

/**
 * Settings sub-screen for the cat-photo library: what it holds, how long it is kept, and one way to
 * clear it. The Settings row that opens this used to do nothing at all.
 *
 * Stateless apart from the two dialogs, whose visibility is ephemeral UI state — the values behind
 * them are persisted through the callbacks.
 */
@Composable
fun PhotoStorageScreen(
    state: PhotoStorageUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onRetentionChange: (Int) -> Unit = {},
    onDeleteAll: () -> Unit = {},
) {
    var showRetentionDialog by remember { mutableStateOf(false) }
    if (showRetentionDialog) {
        OptionPickerDialog(
            title = "Keep photos for",
            options = CaptureRetention.WINDOW_CHOICES,
            selected = state.retentionDays,
            onSelect = onRetentionChange,
            onDismiss = { showRetentionDialog = false },
            label = { CaptureRetention.label(it) },
            supporting = { days ->
                "Favourites are never deleted automatically"
                    .takeIf { days != CaptureRetention.KEEP_FOREVER }
            },
        )
    }

    var showDeleteDialog by remember { mutableStateOf(false) }
    if (showDeleteDialog) {
        ConfirmDialog(
            title = "Delete all photos?",
            message = deleteAllMessage(state),
            confirmLabel = "Delete all",
            destructive = true,
            onConfirm = onDeleteAll,
            onDismiss = { showDeleteDialog = false },
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = Dimens.ScreenHMargin,
            end = Dimens.ScreenHMargin,
            top = 8.dp,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(Dimens.StackGap),
    ) {
        item { BackHeader(title = "Photo storage", onBack = onBack) }

        item {
            SectionLabel(text = "Library", uppercase = true)
            SettingsGroup {
                SettingsRow(
                    title = "Photos",
                    leadingIcon = Icons.Filled.PhotoLibrary,
                    iconTint = MaterialTheme.colorScheme.primary,
                    iconBackground = MaterialTheme.colorScheme.primaryContainer,
                    trailing = { ValueText(state.photoCount.toString()) },
                )
                SettingsRow(
                    title = "Favourites",
                    subtitle = "Kept until you delete them",
                    leadingIcon = Icons.Filled.Star,
                    iconTint = MaterialTheme.colorScheme.tertiary,
                    iconBackground = MaterialTheme.colorScheme.tertiaryContainer,
                    trailing = { ValueText(state.favoriteCount.toString()) },
                )
                SettingsRow(
                    title = "Space used",
                    leadingIcon = Icons.Filled.Storage,
                    iconTint = MaterialTheme.colorScheme.secondary,
                    iconBackground = MaterialTheme.colorScheme.secondaryContainer,
                    trailing = { ValueText(state.storageLabel) },
                )
            }
        }

        item {
            SectionLabel(text = "Retention", uppercase = true)
            SettingsGroup {
                SettingsRow(
                    title = "Keep photos for",
                    subtitle = "Favourites are never removed automatically",
                    leadingIcon = Icons.Filled.AutoDelete,
                    onClick = { showRetentionDialog = true },
                    trailing = { ValueText(state.retentionLabel) },
                )
            }
        }

        item {
            SectionLabel(text = "Danger zone", uppercase = true)
            SettingsGroup {
                SettingsRow(
                    title = "Delete all photos",
                    subtitle = "Includes favourites. This cannot be undone.",
                    leadingIcon = Icons.Filled.DeleteOutline,
                    iconTint = MaterialTheme.colorScheme.error,
                    iconBackground = MaterialTheme.colorScheme.errorContainer,
                    titleColor = MaterialTheme.colorScheme.error,
                    onClick = { showDeleteDialog = true }.takeIf { state.hasPhotos },
                    trailing = { Chevron() },
                )
            }
        }
    }
}

/** Spells out exactly what is about to go, rather than asking a bare "are you sure?". */
private fun deleteAllMessage(state: PhotoStorageUiState): String {
    val photos = if (state.photoCount == 1) "1 photo" else "${state.photoCount} photos"
    val favourites = when (state.favoriteCount) {
        0 -> ""
        1 -> ", including 1 favourite"
        else -> ", including ${state.favoriteCount} favourites"
    }
    return "This deletes all $photos$favourites and frees ${state.storageLabel}. " +
        "Your screen-time history is not affected. This cannot be undone."
}

@Preview(name = "Photo storage", showBackground = true, backgroundColor = 0xFFFFF8F8, heightDp = 900)
@Composable
private fun PhotoStorageScreenPreview() {
    UnPawseTheme { PhotoStorageScreen(state = PhotoStorageUiState.sample()) }
}

@Preview(name = "Photo storage · empty", showBackground = true, backgroundColor = 0xFFFFF8F8, heightDp = 900)
@Composable
private fun PhotoStorageScreenEmptyPreview() {
    UnPawseTheme { PhotoStorageScreen(state = PhotoStorageUiState()) }
}

@Preview(name = "Photo storage · dark", showBackground = true, backgroundColor = 0xFF171213, heightDp = 900)
@Composable
private fun PhotoStorageScreenDarkPreview() {
    UnPawseTheme(darkTheme = true) { PhotoStorageScreen(state = PhotoStorageUiState.sample()) }
}
