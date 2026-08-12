package com.example.unpawse.ui.apppicker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.unpawse.data.usage.AppCategory
import com.example.unpawse.ui.components.OptionPickerDialog

/**
 * Which bucket this app's screen time counts toward in the Stats breakdown.
 *
 * Starts from the platform's own declaration where there is one, which is why this is a correction
 * rather than a chore: most apps land in the right bucket without the user touching it, and the ones
 * the platform never classified sit in "Other" until they do. Same shape as [WeekendLimitControl] —
 * a row that opens an [OptionPickerDialog], stateless apart from the dialog toggle.
 */
@Composable
fun CategoryControl(
    item: AppLimitItem,
    onCategoryChange: (AppCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPicker by remember { mutableStateOf(false) }

    if (showPicker) {
        OptionPickerDialog(
            title = "Category",
            options = AppCategory.entries,
            selected = item.category,
            onSelect = onCategoryChange,
            onDismiss = { showPicker = false },
            label = { it.label },
            supporting = { category ->
                when (category) {
                    AppCategory.SOCIAL -> "Messaging, feeds, and networks"
                    AppCategory.PRODUCTIVITY -> "Work, study, and organisation"
                    AppCategory.ENTERTAINMENT -> "Video, music, games, and photos"
                    AppCategory.OTHER -> "Everything else"
                }
            },
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { showPicker = true }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Category",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = item.category.label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
