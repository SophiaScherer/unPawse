package com.example.unpawse.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * Single-choice dialog for a setting with a small, fixed set of values — reminder frequency, theme
 * mode, retention window, warning threshold. Picking an option commits it and dismisses, so there's
 * no "Save" button to reason about; only "Cancel" backs out.
 *
 * Generic over the option type so callers pass their own enum/int list rather than stringly-typed
 * values, with [label] doing the display formatting.
 */
@Composable
fun <T> OptionPickerDialog(
    title: String,
    options: List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    label: (T) -> String = { it.toString() },
    supporting: ((T) -> String?)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(title) },
        text = {
            Column(Modifier.selectableGroup()) {
                options.forEach { option ->
                    OptionRow(
                        label = label(option),
                        supporting = supporting?.invoke(option),
                        selected = option == selected,
                        onSelect = {
                            onSelect(option)
                            onDismiss()
                        },
                    )
                }
            }
        },
        // No confirm button: choosing an option *is* the confirmation.
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun OptionRow(
    label: String,
    supporting: String?,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // selectable() on the whole row (not just the RadioButton) so the label is a tap target
            // and TalkBack announces one control instead of two.
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (supporting != null) {
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
