package com.example.unpawse.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.unpawse.data.settings.AVATAR_NONE
import com.example.unpawse.data.settings.CatAvatar
import com.example.unpawse.ui.theme.Dimens

/**
 * The preset cats plus a "no picture" tile that keeps the initials avatar. Laid out in rows of
 * three rather than a grid so a later custom-cat option can be appended without a re-layout. Shared
 * by the onboarding step and the Settings profile dialog.
 */
@Composable
fun AvatarPicker(
    selectedId: Int,
    initial: Char,
    onAvatarSelected: (Int) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimens.StackGap),
    ) {
        val options: List<CatAvatar?> = listOf<CatAvatar?>(null) + CatAvatar.entries
        options.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.StackGap),
            ) {
                row.forEach { avatar ->
                    val id = avatar?.id ?: AVATAR_NONE
                    AvatarOption(
                        selected = selectedId == id,
                        label = avatar?.label ?: "No picture",
                        onClick = { onAvatarSelected(id) },
                        modifier = Modifier.weight(1f),
                    ) {
                        if (avatar == null) {
                            InitialsAvatar(initial = initial, size = 64.dp)
                        } else {
                            CatAvatarImage(avatar = avatar, size = 64.dp)
                        }
                    }
                }
                // Keeps the last row's tiles the same width as the full rows above it.
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun AvatarOption(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            // Selectable, not clickable, so TalkBack announces which cat is the current one.
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = Dimens.Base),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                    shape = CircleShape,
                )
                .padding(6.dp),
            contentAlignment = Alignment.Center,
            content = { content() },
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}
