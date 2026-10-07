package com.example.unpawse.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Header for the top-level tabs. Two content variants:
 *  - Home: an avatar + a two-line greeting ("Welcome back," / name).
 *  - Stats/Gallery/Settings: an avatar + the tab's name, so the screen says where the user is.
 * A trailing paw icon is the shared brand signature.
 */
@Composable
fun ScreenHeader(
    modifier: Modifier = Modifier,
    // Deliberately no default: it used to be 'S', so a caller that forgot to pass one silently
    // showed the mockup's initial instead of the user's.
    avatarInitial: Char,
    // Required for the same reason: a header that forgot it would quietly ignore the chosen cat.
    avatarId: Int,
    greeting: String? = null,
    title: String,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ProfileAvatar(avatarId = avatarId, initial = avatarInitial)
        Column(modifier = Modifier.weight(1f)) {
            if (greeting != null) {
                Text(
                    text = greeting,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    // The title is the user's name here; a long one must not wrap the header.
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(
            imageVector = Icons.Filled.Pets,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp),
        )
    }
}
